-- =====================================================================
-- Tehtävä 8: Käyttäjät ja roolit
--
-- Oikeudet annetaan rooleille, ja käyttäjät saavat roolin. Näin saman
-- tason käyttäjiä voi lisätä toistamatta GRANT-lauseita, ja oikeuksia
-- muutetaan yhdessä paikassa.
--
-- Rooli              Käyttäjä          Tarkoitus
-- webstore_admin     webstore_admin    Ylläpito ja skeemamuutokset: kaikki
--                                      oikeudet webstore-kantaan (ei muihin).
-- webstore_api       webstore_app      REST API: vain tarvittavat oikeudet
--                                      taulukohtaisesti. Tilauksia ei voi
--                                      lisätä tai poistaa suoraan, vaan vain
--                                      proseduurien kautta (transaktio +
--                                      saldotarkistus). orderlog on vain
--                                      luettavissa, sitä kirjoittaa liipaisin.
-- webstore_reporting webstore_report   Raportointi/analyysi: vain myynti- ja
--                                      tuotenäkymät. Ei pääsyä asiakkaiden
--                                      henkilötietoihin (nimi, sähköposti,
--                                      puhelin, osoite) -> tietosuoja.
-- webstore_backup    webstore_backup   Varmuuskopiointi: vain luku- ja
--                                      dump-oikeudet, ei muutosoikeuksia.
--
-- Proseduurit ja näkymät on luotu SQL SECURITY DEFINER -tilassa, joten
-- ne suoritetaan luojansa oikeuksin. Siksi API tarvitsee proseduureihin
-- vain EXECUTE-oikeuden ja raportoija näkymiin vain SELECT-oikeuden.
--
-- Vaihda salasanat ennen ajoa. Aja pääkäyttäjänä (root).
-- Tietokannan nimi on 'webstore'; vaihda tarvittaessa.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Roolit
-- ---------------------------------------------------------------------
CREATE ROLE IF NOT EXISTS webstore_admin;
CREATE ROLE IF NOT EXISTS webstore_api;
CREATE ROLE IF NOT EXISTS webstore_reporting;
CREATE ROLE IF NOT EXISTS webstore_backup;

-- Ylläpitäjä: kaikki oikeudet tähän kantaan, myös taulujen, näkymien,
-- proseduurien ja liipaisimien luonti ja muutos.
GRANT ALL PRIVILEGES ON webstore.* TO webstore_admin;

-- API: tuotteet, kategoriat, toimittajat ja asiakkaat (CRUD)
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.products          TO webstore_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.productcategories TO webstore_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.suppliers         TO webstore_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.supplieraddresses TO webstore_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.customers         TO webstore_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.customeraddresses TO webstore_api;
-- API: tilaukset vain luku + tilan päivitys (esim. SHIPPED). Lisäys ja
-- peruminen tehdään proseduureilla.
GRANT SELECT, UPDATE ON webstore.orders     TO webstore_api;
GRANT SELECT         ON webstore.orderitems TO webstore_api;
GRANT SELECT         ON webstore.orderlog   TO webstore_api;
-- API: näkymät ja proseduurit
GRANT SELECT ON webstore.v_product_catalog TO webstore_api;
GRANT SELECT ON webstore.v_order_summary   TO webstore_api;
GRANT SELECT ON webstore.v_product_sales   TO webstore_api;
GRANT SELECT ON webstore.v_monthly_sales   TO webstore_api;
GRANT EXECUTE ON PROCEDURE webstore.place_order  TO webstore_api;
GRANT EXECUTE ON PROCEDURE webstore.cancel_order TO webstore_api;

-- Raportointi: vain koostenäkymät ilman henkilötietoja
GRANT SELECT ON webstore.v_product_sales   TO webstore_reporting;
GRANT SELECT ON webstore.v_monthly_sales   TO webstore_reporting;
GRANT SELECT ON webstore.v_product_catalog TO webstore_reporting;

-- Varmuuskopiointi (mariadb-dump --single-transaction --routines --triggers)
GRANT SELECT, SHOW VIEW, TRIGGER, LOCK TABLES, EVENT, SHOW CREATE ROUTINE
   ON webstore.* TO webstore_backup;

-- ---------------------------------------------------------------------
-- Käyttäjät (vain paikalliset yhteydet: @'localhost')
-- ---------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'webstore_admin'@'localhost'  IDENTIFIED BY 'change_me_admin';
CREATE USER IF NOT EXISTS 'webstore_app'@'localhost'    IDENTIFIED BY 'change_me';
CREATE USER IF NOT EXISTS 'webstore_report'@'localhost' IDENTIFIED BY 'change_me_report';
CREATE USER IF NOT EXISTS 'webstore_backup'@'localhost' IDENTIFIED BY 'change_me_backup';

-- Aiemmin (app_user.sql) webstore_app sai laajat oikeudet koko kantaan.
-- Ne poistetaan, jotta käyttäjällä on vain roolin rajatut oikeudet.
-- Uudessa asennuksessa oikeutta ei ole, jolloin REVOKE antaisi virheen
-- 1141 ja skripti pysähtyisi; käsittelijä ohittaa sen.
DELIMITER //
BEGIN NOT ATOMIC
    DECLARE CONTINUE HANDLER FOR 1141 BEGIN END;
    REVOKE SELECT, INSERT, UPDATE, DELETE, EXECUTE ON webstore.* FROM 'webstore_app'@'localhost';
END //
DELIMITER ;

GRANT webstore_admin     TO 'webstore_admin'@'localhost';
GRANT webstore_api       TO 'webstore_app'@'localhost';
GRANT webstore_reporting TO 'webstore_report'@'localhost';
GRANT webstore_backup    TO 'webstore_backup'@'localhost';

-- Rooli on aktiivinen heti kirjautumisen jälkeen ilman SET ROLE -komentoa
SET DEFAULT ROLE webstore_admin     FOR 'webstore_admin'@'localhost';
SET DEFAULT ROLE webstore_api       FOR 'webstore_app'@'localhost';
SET DEFAULT ROLE webstore_reporting FOR 'webstore_report'@'localhost';
SET DEFAULT ROLE webstore_backup    FOR 'webstore_backup'@'localhost';

-- Tarkistus
SHOW GRANTS FOR webstore_api;
SHOW GRANTS FOR 'webstore_app'@'localhost';
