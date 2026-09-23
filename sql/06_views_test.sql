-- =====================================================================
-- Tehtävä 6: Näkymien testaus
--   mariadb -t webstore -e "source sql/06_views_test.sql"
-- =====================================================================

-- Tuotekatalogi: Työkalut-kategorian halvimmat varastossa olevat tuotteet.
-- Ilman näkymää sama vaatisi kolmen taulun liitoksen.
SELECT id, name, price, stock_quantity, category_name, supplier_name
  FROM v_product_catalog
 WHERE category_id = 5 AND in_stock
 ORDER BY price
 LIMIT 5;

-- Loppuneet tuotteet
SELECT id, name, category_name, supplier_name
  FROM v_product_catalog
 WHERE NOT in_stock;

-- Asiakkaan 1 tilaushistoria summineen
SELECT order_id, order_date, status, item_count, total_quantity, total_amount
  FROM v_order_summary
 WHERE customer_id = 1
 ORDER BY order_date DESC;

-- Näkymään voi yhdistää muita ehtoja ja tauluja kuten tauluun:
-- asiakkaan 1 avoimet (NEW) tilaukset, joiden arvo yli 1000 €
SELECT s.order_id, s.total_amount, a.city
  FROM v_order_summary s
  JOIN orders o ON o.id = s.order_id
  LEFT JOIN customeraddresses a ON a.id = o.shipping_address_id
 WHERE s.customer_id = 1 AND s.status = 'NEW' AND s.total_amount > 1000;

-- Myydyimmät tuotteet
SELECT product_id, product_name, category_name, units_sold, order_count, revenue
  FROM v_product_sales
 ORDER BY units_sold DESC
 LIMIT 10;

-- Kategorioiden myynti näkymästä koostamalla
SELECT category_name, SUM(units_sold) AS units_sold, SUM(revenue) AS revenue
  FROM v_product_sales
 GROUP BY category_name
 ORDER BY revenue DESC;

-- Kuukausimyynti vuodelta 2025
SELECT month, order_count, customer_count, units_sold, revenue, avg_order_value
  FROM v_monthly_sales
 WHERE month LIKE '2025-%'
 ORDER BY month;

-- Näkymän määritelmä ja kyselysuunnitelma: WHERE customer_id = 1
-- työnnetään näkymän sisään (condition pushdown), joten vain asiakkaan 1
-- tilaukset koostetaan.
SHOW CREATE VIEW v_order_summary;
EXPLAIN SELECT * FROM v_order_summary WHERE customer_id = 1;
