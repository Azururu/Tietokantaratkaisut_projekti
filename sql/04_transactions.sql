-- =====================================================================
-- Tehtävä 4: Transaktiot
--
-- Tilanne 1: Tilauksen tekeminen (place_order)
--   Tilaus koostuu useasta kyselystä: orders-rivin lisäys, orderitems-
--   rivien lisäys ja varastosaldojen vähennys. Ilman transaktiota virhe
--   kesken kaiken jättäisi esim. tilauksen ilman tilausrivejä tai
--   vähentäisi saldoa tilaukselle, jota ei koskaan syntynyt. Rinnakkaiset
--   ostajat voisivat myös myydä saman viimeisen tuotteen kahdesti.
--
-- Tilanne 2: Tilauksen peruminen (cancel_order)
--   Tilan muutos CANCELLED-tilaan ja tuotteiden palautus varastoon
--   kuuluvat yhteen. Ilman transaktiota tilaus voisi perua mutta saldo
--   jäädä palauttamatta (tai päinvastoin), ja sama tilaus voitaisiin
--   perua kahdesti, jolloin saldo palautuisi kahteen kertaan.
--
-- Molemmat toteutetaan tallennettuina proseduureina, joita myös REST API
-- kutsuu. Virhetilanteessa EXIT HANDLER perii koko transaktion
-- (ROLLBACK) ja välittää virheen kutsujalle (RESIGNAL).
-- =====================================================================

DROP PROCEDURE IF EXISTS place_order;
DROP PROCEDURE IF EXISTS cancel_order;

DELIMITER //

-- p_items on JSON-taulukko, esim.
--   '[{"product_id": 2, "quantity": 2}, {"product_id": 3, "quantity": 1}]'
CREATE PROCEDURE place_order(
    IN  p_customer_id INT,
    IN  p_address_id  INT,
    IN  p_items       JSON,
    OUT p_order_id    INT
)
BEGIN
    DECLARE v_item_count    INT;
    DECLARE v_found_count   INT;
    DECLARE v_short_count   INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_order_items;
        RESIGNAL;
    END;

    -- Puretaan JSON riveiksi. Sama tuote useaan kertaan yhdistetään.
    DROP TEMPORARY TABLE IF EXISTS tmp_order_items;
    CREATE TEMPORARY TABLE tmp_order_items (
        product_id INT PRIMARY KEY,
        quantity   INT NOT NULL
    );
    INSERT INTO tmp_order_items (product_id, quantity)
    SELECT j.product_id, SUM(j.quantity)
    FROM JSON_TABLE(p_items, '$[*]' COLUMNS (
            product_id INT PATH '$.product_id' ERROR ON EMPTY,
            quantity   INT PATH '$.quantity'   ERROR ON EMPTY
         )) AS j
    GROUP BY j.product_id;

    SELECT COUNT(*) INTO v_item_count FROM tmp_order_items;
    IF v_item_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order must contain at least one item';
    END IF;
    IF EXISTS (SELECT 1 FROM tmp_order_items WHERE quantity <= 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quantity must be positive';
    END IF;

    START TRANSACTION;

    IF NOT EXISTS (SELECT 1 FROM customers WHERE id = p_customer_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer not found';
    END IF;
    IF p_address_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM customeraddresses
        WHERE id = p_address_id AND customer_id = p_customer_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Address does not belong to customer';
    END IF;

    -- Lukitaan tilattavat tuoterivit (FOR UPDATE), jotta rinnakkainen
    -- tilaus ei voi lukea samaa saldoa ennen kuin tämä transaktio päättyy.
    SELECT COUNT(*),
           SUM(p.stock_quantity < t.quantity)
      INTO v_found_count, v_short_count
      FROM tmp_order_items t
      JOIN products p ON p.id = t.product_id
       FOR UPDATE;

    IF v_found_count < v_item_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Product not found';
    END IF;
    IF v_short_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Insufficient stock';
    END IF;

    INSERT INTO orders (customer_id, shipping_address_id, status)
    VALUES (p_customer_id, p_address_id, 'NEW');
    SET p_order_id = LAST_INSERT_ID();

    -- Hinta otetaan tuotetaulusta tilaushetkellä, ei asiakkaalta.
    INSERT INTO orderitems (order_id, product_id, quantity, unit_price)
    SELECT p_order_id, p.id, t.quantity, p.price
      FROM tmp_order_items t
      JOIN products p ON p.id = t.product_id;

    -- version + 1: optimistinen lukitus (tehtävä 14). Ylläpitäjän vanhentunut
    -- tuotelomake ei voi enää kirjoittaa saldomuutoksen yli.
    UPDATE products p
      JOIN tmp_order_items t ON t.product_id = p.id
       SET p.stock_quantity = p.stock_quantity - t.quantity,
           p.version = p.version + 1;

    COMMIT;
    DROP TEMPORARY TABLE IF EXISTS tmp_order_items;
END //

CREATE PROCEDURE cancel_order(IN p_order_id INT)
BEGIN
    DECLARE v_status VARCHAR(50);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Lukitaan tilausrivi: kaksi yhtäaikaista perumista eivät voi
    -- molemmat nähdä tilaa 'NEW' ja palauttaa saldoa kahdesti.
    SELECT status INTO v_status
      FROM orders
     WHERE id = p_order_id
       FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Order not found';
    END IF;
    IF v_status <> 'NEW' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only NEW orders can be cancelled';
    END IF;

    -- orderstatuschange-liipaisin kirjaa muutoksen orderlog-tauluun
    -- samassa transaktiossa.
    UPDATE orders SET status = 'CANCELLED' WHERE id = p_order_id;

    -- version + 1: optimistinen lukitus (tehtävä 14)
    UPDATE products p
      JOIN orderitems oi ON oi.product_id = p.id
       SET p.stock_quantity = p.stock_quantity + oi.quantity,
           p.version = p.version + 1
     WHERE oi.order_id = p_order_id;

    COMMIT;
END //

DELIMITER ;
