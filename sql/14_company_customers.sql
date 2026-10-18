-- =====================================================================
-- Tehtävä 12: periytyminen, yritysasiakas (CompanyCustomer extends Customer)
--
-- Yritysasiakas on asiakas, jolla on kaikki tavallisen asiakkaan tiedot
-- (nimi = yhteyshenkilö, sähköposti, puhelin, osoitteet, tilaukset,
-- profiili) sekä yrityksen omat tiedot:
--   company_name       yrityksen nimi
--   business_id        Y-tunnus (muoto 1234567-8), yksilöllinen
--   vat_number         ALV-tunniste (esim. FI12345678), valinnainen
--   invoice_reference  laskun viite / kustannuspaikka, valinnainen
--
-- Periytymisstrategia: JOINED. Yhteiset kentät ovat customers-taulussa,
-- yrityksen kentät omassa taulussaan, jonka pääavain on samalla
-- viiteavain customers-tauluun. Yritysasiakas = customers-rivi +
-- company_customers-rivi samalla id:llä; tavallisella asiakkaalla
-- company_customers-riviä ei ole.
--
-- Miksi JOINED:
--   SINGLE_TABLE    lisäisi yrityssarakkeet ja tyyppisarakkeen suureen,
--                   järjestelmäversioituun customers-tauluun. Sarakkeet
--                   olisivat tavallisilla asiakkailla NULL, eikä niille
--                   voisi asettaa NOT NULL -rajoitetta.
--   TABLE_PER_CLASS jakaisi asiakkaat kahteen erilliseen tauluun. Tilaukset
--                   ja osoitteet eivät voisi enää viitata yhdellä
--                   viiteavaimella "asiakkaaseen", eikä IDENTITY-avaimia
--                   voi käyttää.
--   JOINED          customers-taulu pysyy ennallaan, kaikki nykyiset
--                   viiteavaimet toimivat, ja yrityksen pakolliset kentät
--                   voivat olla NOT NULL. Hinta: yksi liitos lisää.
--
-- Erotinsaraketta (discriminator) ei tarvita: Hibernate päättelee tyypin
-- siitä, löytyykö asiakkaalle company_customers-rivi.
--
-- Asiakkaan poisto poistaa yritystiedot (ON DELETE CASCADE). Hibernate
-- poistaa aliluokan rivin myös itse ennen customers-riviä.
--
-- Aja webstore_admin-käyttäjänä. Lopun GRANT vaatii pääkäyttäjän (root),
-- koska webstore_adminilla ei ole GRANT OPTION -oikeutta.
-- Peruutus: sql/14_company_customers_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- Aliluokan taulu
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS company_customers (
    customer_id       INT(11)      NOT NULL,
    company_name      VARCHAR(255) NOT NULL,
    business_id       VARCHAR(9)   NOT NULL,
    vat_number        VARCHAR(20)  NULL,
    invoice_reference VARCHAR(50)  NULL,
    PRIMARY KEY (customer_id),
    UNIQUE KEY uq_company_customers_business_id (business_id),
    CONSTRAINT fk_company_customers_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT chk_company_customers_business_id
        CHECK (business_id REGEXP '^[0-9]{7}-[0-9]$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Esimerkkidata: asiakkaista 51-60 tehdään yritysasiakkaita lisäämällä
-- heille company_customers-rivi (customers-tauluun ei kosketa, joten
-- versiohistoriaan ei synny rivejä). Y-tunnukset ovat keksittyjä ja
-- yksilöllisiä (1000051-1, 1000052-2, ...). ALV-tunniste on Suomessa
-- FI + Y-tunnus ilman väliviivaa.
-- INSERT IGNORE: skriptin voi ajaa uudelleen ilman duplikaattivirheitä.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO company_customers
       (customer_id, company_name, business_id, vat_number, invoice_reference)
SELECT c.id,
       CONCAT(c.last_name, ' Oy'),
       CONCAT(1000000 + c.id, '-', c.id % 10),
       CONCAT('FI', 1000000 + c.id, c.id % 10),
       CASE WHEN c.id % 2 = 0 THEN CONCAT('KP-', 100 + c.id) END
  FROM customers c
 WHERE c.id BETWEEN 51 AND 60;

-- ---------------------------------------------------------------------
-- Oikeudet (aja rootina). Lisää sama rivi myös sql/08_security.sql:ään.
-- ---------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.company_customers TO webstore_api;

-- ---------------------------------------------------------------------
-- Tarkistus
-- ---------------------------------------------------------------------
-- Yritysasiakkaiden määrä (esimerkkidatan jälkeen 10).
SELECT COUNT(*) AS company_customer_count FROM company_customers;

-- Sama liitos, jonka Hibernate tekee JOINED-strategialla: kaikki asiakkaat,
-- tyyppi päätellään siitä, löytyykö company_customers-rivi.
SELECT c.id,
       CASE WHEN cc.customer_id IS NULL THEN 'Customer' ELSE 'CompanyCustomer' END AS type,
       CONCAT(c.first_name, ' ', c.last_name) AS contact,
       c.email,
       cc.company_name, cc.business_id, cc.vat_number, cc.invoice_reference
  FROM customers c
  LEFT JOIN company_customers cc ON cc.customer_id = c.id
 WHERE c.id BETWEEN 49 AND 62
 ORDER BY c.id;

-- Rajoitteiden testit (poista kommentti kokeillaksesi):
-- Väärän muotoinen Y-tunnus -> ERROR 4025 CONSTRAINT ... failed
-- INSERT INTO company_customers (customer_id, company_name, business_id) VALUES (61, 'Testi Oy', '123');
-- Sama Y-tunnus kahdelle yritykselle -> ERROR 1062 Duplicate entry
-- INSERT INTO company_customers (customer_id, company_name, business_id) VALUES (61, 'Testi Oy', '1000051-1');
