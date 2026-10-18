-- =====================================================================
-- Tehtävä 12: 1:1-suhde asiakkaan ja asiakasprofiilin välille
--
-- Asiakasprofiili sisältää asiakkaan lisätiedot, joita tilaaminen ja
-- osoitteiden käsittely eivät tarvitse: syntymäaika, asiointikieli,
-- markkinointilupa ja kanta-asiakaspisteet. Jokaisella asiakkaalla on
-- enintään yksi profiili ja jokainen profiili kuuluu täsmälleen yhdelle
-- asiakkaalle -> yksi-yhteen (1:1).
--
-- Miksi oma taulu eikä uusia sarakkeita customers-tauluun:
--   * customers on järjestelmäversioitu (WITH SYSTEM VERSIONING) ja suuri.
--     Sen muuttaminen vaatii erillisen luvan (ks. docs/schema-changes.md),
--     ja jokainen profiilin muutos loisi turhan historiarivin.
--   * Tiedot ovat valinnaisia: kaikilla asiakkailla ei ole profiilia, joten
--     erillinen taulu välttää joukon NULL-sarakkeita.
--   * Useimmat kyselyt (tilaukset, osoitteet) eivät tarvitse profiilia.
--
-- 1:1 varmistetaan kannassa: customer_id on sekä pääavain että viiteavain
-- customers-tauluun (jaettu pääavain). Samalle asiakkaalle ei siis voi
-- syntyä kahta profiilia. JPA:ssa tämä vastaa @OneToOne + @MapsId.
--
-- Asiakkaan poisto poistaa profiilin (ON DELETE CASCADE). Hibernate
-- poistaa profiilin myös itse (cascade = ALL); CASCADE on varmistus.
--
-- Aja webstore_admin-käyttäjänä. Lopun GRANT vaatii pääkäyttäjän (root),
-- koska webstore_adminilla ei ole GRANT OPTION -oikeutta.
-- Peruutus: sql/13_customer_profiles_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- Profiilitaulu
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_profiles (
    customer_id        INT(11)     NOT NULL,
    birth_date         DATE        NULL,
    preferred_language VARCHAR(5)  NOT NULL DEFAULT 'fi',
    marketing_consent  BOOLEAN     NOT NULL DEFAULT FALSE,
    loyalty_points     INT(11)     NOT NULL DEFAULT 0,
    PRIMARY KEY (customer_id),
    CONSTRAINT fk_customer_profiles_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT chk_customer_profiles_points CHECK (loyalty_points >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Esimerkkidata: asiakkaille 1-50 luodaan profiili. Arvot johdetaan
-- id:stä, jotta tulos on sama joka ajolla. Asiakkailla 51-> ei ole
-- profiilia, mikä näyttää suhteen valinnaisuuden.
-- INSERT IGNORE: skriptin voi ajaa uudelleen ilman duplikaattivirheitä.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO customer_profiles
       (customer_id, birth_date, preferred_language, marketing_consent, loyalty_points)
SELECT c.id,
       DATE_ADD('1960-01-01', INTERVAL (c.id * 397) % 15000 DAY),
       CASE c.id % 3 WHEN 0 THEN 'fi' WHEN 1 THEN 'en' ELSE 'sv' END,
       c.id % 2 = 0,
       (c.id * 37) % 500
  FROM customers c
 WHERE c.id BETWEEN 1 AND 50;

-- ---------------------------------------------------------------------
-- Oikeudet (aja rootina). Lisää sama rivi myös sql/08_security.sql:ään.
-- Raportointirooli ei saa oikeutta: syntymäaika on henkilötieto.
-- ---------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.customer_profiles TO webstore_api;

-- ---------------------------------------------------------------------
-- Tarkistus
-- ---------------------------------------------------------------------
-- Profiilien määrä (esimerkkidatan jälkeen 50).
SELECT COUNT(*) AS profile_count FROM customer_profiles;

-- Asiakas ja profiili rinnakkain. LEFT JOIN näyttää myös asiakkaat,
-- joilla profiilia ei ole (profiilin sarakkeet NULL).
SELECT c.id, CONCAT(c.first_name, ' ', c.last_name) AS customer,
       p.birth_date, p.preferred_language, p.marketing_consent, p.loyalty_points
  FROM customers c
  LEFT JOIN customer_profiles p ON p.customer_id = c.id
 WHERE c.id BETWEEN 48 AND 53
 ORDER BY c.id;

-- 1:1-rajoitteen testi: toinen profiili samalle asiakkaalle epäonnistuu
-- (ERROR 1062 Duplicate entry '1' for key 'PRIMARY'). Poista kommentti
-- kokeillaksesi.
-- INSERT INTO customer_profiles (customer_id) VALUES (1);
