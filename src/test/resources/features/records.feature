Feature: Changing records in ZCBS
  The app edits a record through the ZCBS form: it opens the form, checks whether anything changed in the meantime, saves and reads back.

  Background:
    Given Carla is logged in

  Scenario: Changing the title of a record
    When Carla opens record 99003 for editing
    Then Carla sees the title "Kerkstraat 1960, de kerk van Heemskerk" in the form
    When Carla saves the title "De kerk aan de Kerkstraat, 1960"
    Then record 99003 is saved for Carla
    And ZCBS has the title "De kerk aan de Kerkstraat, 1960" for record 99003
    And ZCBS stored the title of record 99003 as last changed by Carla

  Scenario: Special characters survive the round trip
    When Carla opens record 99003 for editing
    And Carla saves the title "Café \"De Zon\" & één ëi"
    Then record 99003 is saved for Carla
    When Carla opens record 99003 for editing
    Then Carla sees the title "Café \"De Zon\" & één ëi" in the form

  Scenario: A description of more than one line
    When Carla opens record 99067 for editing
    And Carla saves the description "Eerste regel\nTweede regel"
    Then ZCBS has the description "Eerste regel <br>Tweede regel" for record 99067

  Scenario: Saving without changes sends nothing
    When Carla opens record 99003 for editing
    And Carla saves the form without changes
    Then Carla is told that nothing changed
    And record 99003 is unchanged in ZCBS

  Scenario: Preview shows what will change
    When Carla opens record 99003 for editing
    And Carla previews the title "Nieuwe titel"
    Then Carla sees "Nieuwe titel"
    And record 99003 is unchanged in ZCBS

  Scenario: Cancelling leaves the record as it was
    When Carla opens record 99003 for editing
    And Carla cancels editing
    Then record 99003 is unchanged in ZCBS

  Scenario: A record that does not exist
    When Carla opens record 12345 for editing
    Then Carla sees "Record 12345 bestaat niet in ZCBS"

  Scenario: Something that is not a number
    When Carla opens record abc for editing
    Then Carla sees "Vul één recordnummer in"

  Scenario: A record somebody else has open is locked
    Given Bob has opened record 99003 for editing
    When Carla opens record 99003 for editing
    Then Carla is refused with "record-locked"
    And Carla sees "Bob"
    And Carla sees "minuten"

  Scenario: Somebody changed another field in ZCBS: both changes stay
    When Carla opens record 99003 for editing
    And someone changes the keyword of record 99003 to "kerk" directly in ZCBS
    And Carla saves the title "Mijn titel"
    Then record 99003 is saved for Carla
    And ZCBS has the title "Mijn titel" for record 99003
    And ZCBS has the keyword "kerk" for record 99003

  Scenario: Somebody changed the same field in ZCBS: keeping my own version
    When Carla opens record 99003 for editing
    And someone changes the title of record 99003 to "Hun titel" directly in ZCBS
    And Carla saves the title "Mijn titel"
    Then Carla is refused with "edit-conflict"
    And Carla sees "Hun titel"
    And ZCBS has the title "Hun titel" for record 99003
    When Carla keeps her own version of the conflict
    Then ZCBS has the title "Mijn titel" for record 99003

  Scenario: Somebody changed the same field in ZCBS: taking their version
    When Carla opens record 99003 for editing
    And someone changes the title of record 99003 to "Hun titel" directly in ZCBS
    And Carla saves the title "Mijn titel"
    And Carla takes the version from ZCBS in the conflict
    Then Carla sees the title "Hun titel" in the form
    And ZCBS has the title "Hun titel" for record 99003

  Scenario: A word that ZCBS refuses is stopped before anything is sent
    When Carla opens record 99003 for editing
    And Carla saves the title "De UNION van werknemers"
    Then Carla is refused with "save-blocked"
    And Carla sees "UNION"
    And record 99003 is unchanged in ZCBS

  Scenario: A character that ZCBS cannot store is stopped before anything is sent
    When Carla opens record 99003 for editing
    And Carla saves the title "Prijs € 5"
    Then Carla is refused with "save-blocked"
    And Carla sees "€"
    And record 99003 is unchanged in ZCBS

  @alleen-nabouw
  Scenario: The ZCBS session expires while editing: the app logs in again by itself
    When Carla opens record 99003 for editing
    And all ZCBS sessions expire
    And Carla saves the title "Na een verlopen sessie"
    Then record 99003 is saved for Carla
    And ZCBS has the title "Na een verlopen sessie" for record 99003

  Scenario: Someone who may only read in ZCBS cannot edit
    Given Rob may only read records in ZCBS
    When Rob logs in
    And Rob opens record 99003 for editing
    Then Rob cannot edit the record
    And record 99003 is unchanged in ZCBS

  Scenario: Opening the scan of a record
    Then Carla sees the scan of record 99003 as a pdf
    And Carla is told that record 12345 has no scan

  Scenario: Opening a record in ZCBS itself, with the same login
    When Carla opens record 99003 in ZCBS itself
    Then ZCBS itself shows "Kerkstraat 1960, de kerk van Heemskerk"
