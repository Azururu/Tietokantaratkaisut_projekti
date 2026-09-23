-- =====================================================================
-- Merkistövirheiden korjaus (aja kerran dumpin latauksen jälkeen)
--
-- project_database_utf8.sql:ssä osa ääkkösistä on tallentunut väärin:
-- UTF-8-teksti on luettu Windowsin konsolin CP850-merkistönä ja
-- tallennettu uudelleen UTF-8:na, esim. 'Työkalut' -> 'Ty├Âkalut'.
-- Korjaus tekee muunnoksen toisin päin. Vain rivit, joissa on
-- virheelle tyypillisiä merkkejä (├ ┬ └ ┼), käsitellään, joten oikein
-- tallennettu teksti ei muutu ja skriptin voi ajaa useasti.
-- =====================================================================

UPDATE productcategories
   SET name        = CONVERT(CAST(CONVERT(name USING cp850) AS BINARY) USING utf8mb4),
       description = CONVERT(CAST(CONVERT(description USING cp850) AS BINARY) USING utf8mb4)
 WHERE CONCAT_WS(' ', name, description) REGEXP '[├┬└┼]';

UPDATE suppliers
   SET name         = CONVERT(CAST(CONVERT(name USING cp850) AS BINARY) USING utf8mb4),
       contact_name = CONVERT(CAST(CONVERT(contact_name USING cp850) AS BINARY) USING utf8mb4),
       email        = CONVERT(CAST(CONVERT(email USING cp850) AS BINARY) USING utf8mb4)
 WHERE CONCAT_WS(' ', name, contact_name, email) REGEXP '[├┬└┼]';

UPDATE supplieraddresses
   SET street_address = CONVERT(CAST(CONVERT(street_address USING cp850) AS BINARY) USING utf8mb4),
       city           = CONVERT(CAST(CONVERT(city USING cp850) AS BINARY) USING utf8mb4),
       country        = CONVERT(CAST(CONVERT(country USING cp850) AS BINARY) USING utf8mb4)
 WHERE CONCAT_WS(' ', street_address, city, country) REGEXP '[├┬└┼]';
