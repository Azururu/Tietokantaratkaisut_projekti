-- =====================================================================
-- Tehtävä 12: peruutus skriptille 13_customer_profiles.sql
--
-- Poistaa profiilitaulun ja sen tiedot. customers-tauluun ei koskettu.
--
-- Ota ensin käyttöön API-versio, joka ei kuvaa CustomerProfile-entiteettiä:
-- ddl-auto=validate kaataa käynnistyksen, jos entiteetti viittaa
-- puuttuvaan tauluun.
-- =====================================================================

-- Aja rootina (vaatii GRANT OPTION -oikeuden). MariaDB ei poista
-- taulukohtaisia oikeuksia DROP TABLEn yhteydessä, joten ne perutaan ensin.
REVOKE SELECT, INSERT, UPDATE, DELETE ON webstore.customer_profiles FROM webstore_api;

DROP TABLE IF EXISTS customer_profiles;
