-- =====================================================================
-- Tehtävä 12: peruutus skriptille 12_product_suppliers.sql
--
-- Poistaa liitostaulun. products.supplier_id:hen ei koskettu, joten
-- tuotteiden päätoimittajat säilyvät. Esimerkkidatan lisätoimittajat
-- häviävät taulun mukana.
--
-- Ota ensin käyttöön API-versio, joka ei kuvaa Product.suppliers-kenttää:
-- ddl-auto=validate kaataa käynnistyksen, jos entiteetti viittaa
-- puuttuvaan tauluun.
-- =====================================================================

-- Aja rootina (vaatii GRANT OPTION -oikeuden). MariaDB ei poista
-- taulukohtaisia oikeuksia DROP TABLEn yhteydessä, joten ne perutaan ensin.
REVOKE SELECT, INSERT, DELETE ON webstore.product_suppliers FROM webstore_api;

DROP TABLE IF EXISTS product_suppliers;
