-- =====================================================================
-- Tehtävä 14: peruutus skriptille 15_product_version.sql
--
-- Järjestys:
--   1. Ota käyttöön API-versio, jossa Product- ja CatalogProduct-
--      entiteeteillä ei ole version-kenttää: ddl-auto=validate kaataa
--      käynnistyksen, jos entiteetti viittaa puuttuvaan sarakkeeseen.
--   2. Palauta 04_transactions.sql ja 06_views.sql versioon, joka ei
--      käytä products.version-saraketta, ja aja ne. Muuten proseduurit ja
--      näkymä viittaavat poistettuun sarakkeeseen.
--   3. Aja tämä skripti.
-- =====================================================================

ALTER TABLE products DROP COLUMN IF EXISTS version, ALGORITHM=INSTANT;
