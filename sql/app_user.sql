-- =====================================================================
-- Sovelluksen tietokantakäyttäjä
--
-- REST API ei käytä root-tunnusta. webstore_app saa vain ne oikeudet,
-- joita API tarvitsee: datan luku/kirjoitus ja proseduurien suoritus.
-- Ei oikeutta muuttaa rakennetta (CREATE/ALTER/DROP) eikä muihin
-- tietokantoihin.
--
-- Vaihda salasana ennen ajoa ja laita sama salasana .env-tiedostoon.
-- Tietokannan nimi: vaihda webstore tarvittaessa (esim. webstore_scratch).
-- =====================================================================

CREATE USER IF NOT EXISTS 'webstore_app'@'localhost' IDENTIFIED BY 'change_me';

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE
   ON webstore.* TO 'webstore_app'@'localhost';
