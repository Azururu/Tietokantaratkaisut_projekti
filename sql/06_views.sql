-- =====================================================================
-- Tehtävä 6: Näkymät
--
-- v_product_catalog   Tuotelista kategoria- ja toimittajanimineen.
--                     Verkkokaupan tuotelistaus ilman kolmen taulun
--                     liitosta joka kyselyssä. Näyttää toimittajasta vain
--                     nimen: yhteyshenkilö, puhelin ja sähköposti jäävät
--                     pois (tietoturva: asiakkaalle näkyvä data rajattu).
--
-- v_order_summary     Tilausten yhteenveto: asiakkaan nimi, rivien ja
--                     kappaleiden määrä sekä tilauksen loppusumma.
--                     Asiakkaan tilaushistoria ilman, että jokainen kysely
--                     toistaa liitokset ja SUM/COUNT-laskennan.
--
-- v_product_sales     Tuotekohtainen myynti (kappaleet, tilaukset,
--                     liikevaihto), perutut tilaukset poissuljettuina.
--                     Myydyimmät tuotteet -listaus.
--
-- v_monthly_sales     Myynti kuukausittain (tilaukset, asiakkaat,
--                     liikevaihto, keskiostos). Raportointia varten.
--
-- Kaikki summat lasketaan orderitems.unit_price-kentästä, eli
-- tilaushetken hinnasta, ei tuotteen nykyisestä hinnasta.
-- =====================================================================

CREATE OR REPLACE VIEW v_product_catalog AS
SELECT p.id,
       p.name,
       p.description,
       p.price,
       p.stock_quantity,
       p.stock_quantity > 0 AS in_stock,
       p.category_id,
       c.name AS category_name,
       p.supplier_id,
       s.name AS supplier_name,
       p.version   -- optimistinen lukitus (tehtävä 14): asiakas lähettää tämän takaisin PUTissa
  FROM products p
  LEFT JOIN productcategories c ON c.id = p.category_id
  LEFT JOIN suppliers s ON s.id = p.supplier_id;

-- customer_id ja status ovat GROUP BY:ssa, jotta niihin kohdistuvat
-- WHERE-ehdot voidaan työntää näkymän sisään (condition pushdown).
-- Silloin esim. yhden asiakkaan tilauksia haettaessa ei koosteta kaikkia
-- 200 000 tilausta.
CREATE OR REPLACE VIEW v_order_summary AS
SELECT o.id AS order_id,
       o.customer_id,
       CONCAT(c.first_name, ' ', c.last_name) AS customer_name,
       o.order_date,
       o.delivery_date,
       o.status,
       COUNT(oi.product_id) AS item_count,
       CAST(COALESCE(SUM(oi.quantity), 0) AS SIGNED) AS total_quantity,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_amount
  FROM orders o
  JOIN customers c ON c.id = o.customer_id
  LEFT JOIN orderitems oi ON oi.order_id = o.id
 GROUP BY o.id, o.customer_id, c.first_name, c.last_name,
          o.order_date, o.delivery_date, o.status;

CREATE OR REPLACE VIEW v_product_sales AS
SELECT p.id AS product_id,
       p.name AS product_name,
       c.name AS category_name,
       CAST(SUM(oi.quantity) AS SIGNED) AS units_sold,
       COUNT(DISTINCT oi.order_id) AS order_count,
       SUM(oi.quantity * oi.unit_price) AS revenue
  FROM orderitems oi
  JOIN orders o ON o.id = oi.order_id AND o.status <> 'CANCELLED'
  JOIN products p ON p.id = oi.product_id
  LEFT JOIN productcategories c ON c.id = p.category_id
 GROUP BY p.id, p.name, c.name;

CREATE OR REPLACE VIEW v_monthly_sales AS
SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS month,
       COUNT(DISTINCT o.id) AS order_count,
       COUNT(DISTINCT o.customer_id) AS customer_count,
       CAST(SUM(oi.quantity) AS SIGNED) AS units_sold,
       SUM(oi.quantity * oi.unit_price) AS revenue,
       ROUND(SUM(oi.quantity * oi.unit_price) / COUNT(DISTINCT o.id), 2) AS avg_order_value
  FROM orders o
  JOIN orderitems oi ON oi.order_id = o.id
 WHERE o.status <> 'CANCELLED'
 GROUP BY DATE_FORMAT(o.order_date, '%Y-%m');
