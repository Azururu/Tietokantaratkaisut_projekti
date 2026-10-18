-- =====================================================================
-- Tehtävä 12: N:M-suhde tuotteiden ja toimittajien välille
--
-- Aiemmin tuotteella oli yksi toimittaja (products.supplier_id, 1:M).
-- Käytännössä samaa tuotetta voi tilata usealta toimittajalta (esim.
-- varatoimittaja, kun päätoimittajalta loppuu varasto), ja jokainen
-- toimittaja toimittaa useita tuotteita -> moni-moneen (N:M).
--
-- N:M toteutetaan liitostaululla product_suppliers, jonka jokainen rivi
-- kertoo "tuotetta X saa toimittajalta Y". Pääavain on (product_id,
-- supplier_id), joten sama pari ei voi esiintyä kahdesti.
--
-- Liitostaulussa ei ole omia kenttiä, joten JPA:ssa se mallinnetaan
-- suoraan @ManyToMany + @JoinTable -annotaatioilla (ei omaa entiteettiä).
-- Jos parille tarvittaisiin omaa tietoa (esim. ostohinta toimittajittain),
-- taulusta tulisi oma entiteetti, kuten orderitems.
--
-- products.supplier_id jätetään paikalleen "päätoimittajaksi"
-- (expand-then-contract, ks. docs/schema-changes.md): v_product_catalog
-- ja nykyinen API toimivat muuttumattomina.
--
-- Viiteavainten poistosäännöt:
--   tuote poistetaan   -> sen liitosrivit poistuvat (ON DELETE CASCADE).
--                         Hibernate poistaa ne myös itse, koska Product
--                         omistaa suhteen; CASCADE on varmistus.
--   toimittaja poistetaan -> estetään, jos sillä on vielä tuotteita
--                         (RESTRICT), kuten products.supplier_id:ssä.
--
-- Aja webstore_admin-käyttäjänä. Lopun GRANT vaatii pääkäyttäjän (root),
-- koska webstore_adminilla ei ole GRANT OPTION -oikeutta.
-- Peruutus: sql/12_product_suppliers_rollback.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- Liitostaulu
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_suppliers (
    product_id  INT(11) NOT NULL,
    supplier_id INT(11) NOT NULL,
    PRIMARY KEY (product_id, supplier_id),
    -- Pääavain kattaa haut tuotteen mukaan; tämä indeksi nopeuttaa hakua
    -- "kaikki toimittajan tuotteet".
    KEY idx_product_suppliers_supplier (supplier_id),
    CONSTRAINT fk_product_suppliers_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_suppliers_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Nykyiset tiedot: jokaisen tuotteen nykyinen toimittaja liitostauluun.
-- INSERT IGNORE: skriptin voi ajaa uudelleen ilman duplikaattivirheitä.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO product_suppliers (product_id, supplier_id)
SELECT id, supplier_id
  FROM products
 WHERE supplier_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Esimerkkidata: tuotteille 1-20 lisätään toinen toimittaja (seuraava
-- toimittaja id-järjestyksessä, 100:n jälkeen takaisin 1:een), jotta
-- datassa on oikeasti tuotteita, joilla on useita toimittajia.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO product_suppliers (product_id, supplier_id)
SELECT p.id, (p.supplier_id % 100) + 1
  FROM products p
 WHERE p.id BETWEEN 1 AND 20
   AND p.supplier_id IS NOT NULL
   AND EXISTS (SELECT 1 FROM suppliers s WHERE s.id = (p.supplier_id % 100) + 1);

-- ---------------------------------------------------------------------
-- Oikeudet (aja rootina). Lisää sama rivi myös sql/08_security.sql:ään.
-- API lisää ja poistaa liitoksia; rivejä ei koskaan päivitetä.
-- ---------------------------------------------------------------------
GRANT SELECT, INSERT, DELETE ON webstore.product_suppliers TO webstore_api;

-- ---------------------------------------------------------------------
-- Tarkistus
-- ---------------------------------------------------------------------
-- Jokaisella toimittajallisella tuotteella pitää olla vähintään yksi rivi
-- (tuloksen pitää olla 0).
SELECT COUNT(*) AS products_missing_link
  FROM products p
 WHERE p.supplier_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM product_suppliers ps WHERE ps.product_id = p.id);

-- Tuotteet, joilla on useita toimittajia (esimerkkidatan 20 tuotetta).
SELECT p.id, p.name, GROUP_CONCAT(s.name ORDER BY s.id SEPARATOR ', ') AS suppliers
  FROM products p
  JOIN product_suppliers ps ON ps.product_id = p.id
  JOIN suppliers s ON s.id = ps.supplier_id
 GROUP BY p.id, p.name
HAVING COUNT(*) > 1
 ORDER BY p.id;

-- Esimerkki: kaikki toimittajan 1 tuotteet.
SELECT p.id, p.name, p.price
  FROM product_suppliers ps
  JOIN products p ON p.id = ps.product_id
 WHERE ps.supplier_id = 1
 ORDER BY p.name;
