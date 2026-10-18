-- =====================================================================
-- Tehtävä 12: peruutus skriptille 14_company_customers.sql
--
-- Poistaa yritysasiakkaiden lisätiedot. customers-tauluun ei koskettu,
-- joten yritysasiakkaat jäävät tavallisiksi asiakkaiksi (nimi,
-- sähköposti, osoitteet ja tilaukset säilyvät).
--
-- Ota ensin käyttöön API-versio, jossa ei ole CompanyCustomer-entiteettiä:
-- ddl-auto=validate kaataa käynnistyksen, jos entiteetti viittaa
-- puuttuvaan tauluun.
-- =====================================================================

-- Aja rootina (vaatii GRANT OPTION -oikeuden). MariaDB ei poista
-- taulukohtaisia oikeuksia DROP TABLEn yhteydessä, joten ne perutaan ensin.
REVOKE SELECT, INSERT, UPDATE, DELETE ON webstore.company_customers FROM webstore_api;

DROP TABLE IF EXISTS company_customers;
