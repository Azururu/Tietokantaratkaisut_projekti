-- =====================================================================
-- Tehtävä 4: Transaktioiden testaus
-- Aja kohta kerrallaan (esim. HeidiSQL) tai kokonaan:
--   mariadb --force webstore < sql/04_transactions_test.sql
-- Testituotteet: 2 ja 3 ovat varastossa, tuotteen 1 saldo on 0.
-- =====================================================================

-- Lähtötilanne
SELECT id, stock_quantity FROM products WHERE id IN (1, 2, 3);
SELECT COUNT(*) AS orders_before FROM orders;

-- ---------------------------------------------------------------------
-- Testi 1: Onnistunut tilaus
-- Odotus: uusi tilaus + 2 tilausriviä, saldot 2 -> -2 ja 3 -> -1
-- ---------------------------------------------------------------------
CALL place_order(1, 1, '[{"product_id": 2, "quantity": 2}, {"product_id": 3, "quantity": 1}]', @order_id);
SELECT @order_id AS new_order_id;
SELECT * FROM orders WHERE id = @order_id;
SELECT * FROM orderitems WHERE order_id = @order_id;
SELECT id, stock_quantity FROM products WHERE id IN (1, 2, 3);

-- ---------------------------------------------------------------------
-- Testi 2: Virhe kesken tilauksen (tuotteen 1 saldo ei riitä)
-- Odotus: virhe 'Insufficient stock', ei uutta tilausta, tuotteen 2
-- saldo ei muutu vaikka se oli samassa tilauksessa
-- ---------------------------------------------------------------------
CALL place_order(1, 1, '[{"product_id": 2, "quantity": 1}, {"product_id": 1, "quantity": 1}]', @order_id2);
SELECT COUNT(*) AS orders_after_failed FROM orders;
SELECT id, stock_quantity FROM products WHERE id IN (1, 2, 3);

-- ---------------------------------------------------------------------
-- Testi 3: Olematon tuote / väärän asiakkaan osoite
-- Odotus: virheet 'Product not found' ja 'Address does not belong to
-- customer', ei muutoksia
-- ---------------------------------------------------------------------
CALL place_order(1, 1, '[{"product_id": 999999, "quantity": 1}]', @order_id3);
CALL place_order(1, 2, '[{"product_id": 2, "quantity": 1}]', @order_id4);
SELECT COUNT(*) AS orders_after_invalid FROM orders;

-- ---------------------------------------------------------------------
-- Testi 4: Tilauksen peruminen
-- Odotus: tila CANCELLED, saldot palautuvat, orderlog-rivi syntyy
-- liipaisimen kautta. Toinen peruminen epäonnistuu.
-- ---------------------------------------------------------------------
CALL cancel_order(@order_id);
SELECT id, status FROM orders WHERE id = @order_id;
SELECT id, stock_quantity FROM products WHERE id IN (1, 2, 3);
SELECT * FROM orderlog WHERE order_id = @order_id;
CALL cancel_order(@order_id);

-- ---------------------------------------------------------------------
-- Testi 5: Käsin kirjoitettu transaktio ja ROLLBACK
-- Näyttää, että ROLLBACK perii kaikki jo tehdyt muutokset
-- ---------------------------------------------------------------------
START TRANSACTION;
INSERT INTO orders (customer_id, status) VALUES (1, 'NEW');
SET @manual_id = LAST_INSERT_ID();
INSERT INTO orderitems (order_id, product_id, quantity, unit_price) VALUES (@manual_id, 2, 5, 546.08);
UPDATE products SET stock_quantity = stock_quantity - 5 WHERE id = 2;
SELECT id, stock_quantity FROM products WHERE id = 2;   -- transaktion sisällä saldo on vähentynyt
ROLLBACK;
SELECT COUNT(*) AS manual_order_exists FROM orders WHERE id = @manual_id;  -- 0
SELECT id, stock_quantity FROM products WHERE id = 2;   -- alkuperäinen saldo

-- ---------------------------------------------------------------------
-- Testi 6: Rinnakkaisuus (kaksi istuntoa)
-- Istunto A:
--   START TRANSACTION;
--   SELECT stock_quantity FROM products WHERE id = 2 FOR UPDATE;
--   -- (älä vielä commitoi)
-- Istunto B:
--   CALL place_order(2, NULL, '[{"product_id": 2, "quantity": 1}]', @x);
--   -- jää odottamaan lukkoa
-- Istunto A:
--   COMMIT;
-- Odotus: istunnon B kutsu jatkuu vasta A:n COMMITin jälkeen ja lukee
-- ajantasaisen saldon, joten samaa kappaletta ei myydä kahdesti.
-- ---------------------------------------------------------------------
