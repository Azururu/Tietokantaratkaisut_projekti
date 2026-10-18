-- =====================================================================
-- Tehtävä 14: optimistinen lukitus tuotteille (products.version)
--
-- Ongelma: kaksi ylläpitäjää avaa saman tuotteen. A muuttaa hinnan ja
-- tallentaa, B tallentaa sen jälkeen vanhan lomakkeensa (esim. saldo-
-- korjaus) ja A:n hinnanmuutos katoaa huomaamatta ("lost update"). Sama
-- tapahtuu, jos saldo muuttuu tilauksen tai massapäivityksen takia sillä
-- välin, kun lomake on auki.
--
-- Ratkaisu: versionumero. Jokainen muutos kasvattaa sitä, ja päivitys
-- onnistuu vain, jos versio on yhä sama kuin muokkauksen alussa:
--   UPDATE products SET ..., version = version + 1 WHERE id = ? AND version = ?
-- Jos rivejä päivittyy 0, joku muu ehti ensin -> API vastaa 409 Conflict.
--
-- Miksi optimistinen eikä pessimistinen (SELECT ... FOR UPDATE):
-- tuotetietojen muokkaus on harvinaista ja kestää minuutteja (lomake auki).
-- Tietokantalukkoa ei voi pitää niin kauan, joten ristiriita havaitaan
-- vasta tallennettaessa. Tilaukset ja saldon varaus käyttävät edelleen
-- pessimististä lukitusta (04_transactions.sql), koska siellä ristiriidat
-- ovat yleisiä eikä ostos saa epäonnistua.
--
-- Kaikki products-taulua muuttavat polut kasvattavat versiota:
--   PUT /api/products/{id}     JPA @Version (automaattinen)
--   place_order, cancel_order  04_transactions.sql (päivitetty)
--   massapäivitykset           ProductRepository / ProductRepositoryCustomImpl
--
-- Ajojärjestys (webstore_admin tai root):
--   1. tämä skripti (lisää sarakkeen)
--   2. sql/04_transactions.sql (proseduurit kasvattavat versiota)
--   3. sql/06_views.sql (v_product_catalog palauttaa version)
-- Proseduurien uudelleenluonnin jälkeen tarkista, että API saa yhä ajaa
-- ne: SHOW GRANTS FOR webstore_api; tarvittaessa aja rootina
--   GRANT EXECUTE ON PROCEDURE webstore.place_order  TO webstore_api;
--   GRANT EXECUTE ON PROCEDURE webstore.cancel_order TO webstore_api;
--
-- Peruutus: sql/15_product_version_rollback.sql
-- =====================================================================

-- Nykyiset tuotteet saavat version 0. INSTANT: taulua ei kopioida eikä lukita.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0,
    ALGORITHM=INSTANT;

-- Tarkistus: kaikilla tuotteilla versio 0
SELECT version, COUNT(*) AS products FROM products GROUP BY version;
