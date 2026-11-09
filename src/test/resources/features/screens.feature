@screens
Feature: The screens of the frontend
  The other features go through the API. These open a real browser, to see that the screens use that API correctly.

  Scenario: Logging in and choosing a collection
    Given Carla may edit records in ZCBS
    When the app is opened at "/"
    Then the screen is titled "Welkom bij ZcbsServer"
    When Carla logs in on the screen
    Then the screen is titled "Met welke collectie wil je werken?"
    And the screen says "Rechten in ZCBS"
    And the collection "archief" can be chosen on the screen
    And the collection "artikelen" cannot be chosen on the screen
    When the collection "archief" is chosen on the screen
    Then the screen is titled "Collectie archief"
    And the browser is at "/collectie/archief"

  Scenario: A wrong password shows the message from the API
    Given Carla may edit records in ZCBS
    When the app is opened at "/"
    And Carla logs in on the screen with the password "verkeerd"
    Then the screen says "Naam of wachtwoord niet herkend"
    And the screen is titled "Welkom bij ZcbsServer"

  Scenario: Reloading a screen keeps you there
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief"
    And the page is reloaded
    Then the screen is titled "Collectie archief"

  Scenario: A link while not logged in returns there after logging in
    Given Carla may edit records in ZCBS
    When the app is opened at "/collectie/archief"
    Then the screen says "Log eerst in bij ZCBS."
    When Carla logs in on the screen
    Then the screen is titled "Collectie archief"
    And the browser is at "/collectie/archief"

  Scenario: A collection without rights goes back to the choice
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/artikelen"
    Then the screen is titled "Met welke collectie wil je werken?"

  Scenario: Logging out on the screen
    Given Carla is logged in on the screen
    When "Uitloggen" is clicked
    Then the screen is titled "Welkom bij ZcbsServer"
    When the page is reloaded
    Then the screen is titled "Welkom bij ZcbsServer"

  Scenario: The admin logs in and out on the screen
    When the app is opened at "/beheer"
    And the admin logs in on the screen with the password "verkeerd"
    Then the screen says "Dat wachtwoord klopt niet."
    When the admin logs in on the screen with the right password
    Then the screen is titled "Je bent ingelogd als beheerder"
    When "Uitloggen als beheerder" is clicked
    Then the screen is titled "Welkom bij ZcbsServer"

  Scenario: A screen that does not exist says so
    When the app is opened at "/bestaat-niet"
    Then the screen is titled "Dit scherm bestaat niet"

  Scenario: The files of the frontend
    When the app is opened at "/"
    Then the frontend is served with its own scripts
    And the file "/assets/bestaat-niet.js" does not exist
    And the file "/bestaat-niet.png" does not exist

  # ---- Records ----

  Scenario: Changing a record: from the number to back in the list
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records"
    And "Recordnummer" is filled in as "99003"
    And "Bewerk" is clicked
    Then the title of the record on the screen is "Kerkstraat 1960, de kerk van Heemskerk"
    And the screen says "voor anderen vergrendeld"
    When the title of the record is changed to "De kerk aan de Kerkstraat" on the screen
    And "Opslaan" is clicked
    Then the screen says "Record 99003 is opgeslagen en teruggecontroleerd"
    And ZCBS has the title "De kerk aan de Kerkstraat" for record 99003
    And the screen is titled "Records aanpassen"

  Scenario: Saving without a change says so
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    And "Opslaan" is clicked
    Then the screen says "Er is niets gewijzigd"
    And record 99003 is unchanged in ZCBS

  Scenario: Reloading the form of a record keeps it open
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    And the page is reloaded
    Then the title of the record on the screen is "Kerkstraat 1960, de kerk van Heemskerk"

  Scenario: Cancelling goes back to the list without saving
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    And the title of the record is changed to "Toch niet" on the screen
    And "Annuleren" is clicked
    Then the screen is titled "Records aanpassen"
    And record 99003 is unchanged in ZCBS

  Scenario: A number that does not exist, and something that is no number
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records"
    And "Recordnummer" is filled in as "12345"
    And "Bewerk" is clicked
    Then the screen says "Record 12345 bestaat niet in ZCBS"
    And the screen is titled "Records aanpassen"
    When "Recordnummer" is filled in as "abc"
    And "Bewerk" is clicked
    Then the screen says "Vul één recordnummer in"

  Scenario: A record somebody else has open
    Given Bob has opened record 99003 for editing
    And Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    Then the screen says "minuten"
    And the screen says "Probeer het over een paar minuten opnieuw"
    When "Annuleren" is clicked
    Then the screen is titled "Records aanpassen"

  Scenario: Somebody changed the same field: keeping my own version
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    Then the title of the record on the screen is "Kerkstraat 1960, de kerk van Heemskerk"
    When someone changes the title of record 99003 to "Hun titel" directly in ZCBS
    And the title of the record is changed to "Mijn titel" on the screen
    And "Opslaan" is clicked
    Then the screen says "Deze velden zijn bij jullie allebei anders"
    And the screen says "Hun titel"
    When "Mijn versie bewaren" is clicked
    Then the screen says "Record 99003 is opgeslagen en teruggecontroleerd"
    And ZCBS has the title "Mijn titel" for record 99003

  Scenario: Somebody changed the same field: taking their version
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    Then the title of the record on the screen is "Kerkstraat 1960, de kerk van Heemskerk"
    When someone changes the title of record 99003 to "Hun titel" directly in ZCBS
    And the title of the record is changed to "Mijn titel" on the screen
    And "Opslaan" is clicked
    And "Die van ZCBS gebruiken" is clicked
    Then the screen says "vervangen door de versie uit ZCBS"
    And the title of the record on the screen is "Hun titel"

  Scenario: A word that ZCBS refuses shows why
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records/99003"
    And the title of the record is changed to "De UNION van werknemers" on the screen
    And "Opslaan" is clicked
    Then the screen says "Dit kan zo niet worden opgeslagen"
    And the screen says "UNION"
    And record 99003 is unchanged in ZCBS

  Scenario: Looking at the scan, and opening the record in ZCBS itself
    Given Carla is logged in on the screen
    When the app is opened at "/collectie/archief/records"
    And "Recordnummer" is filled in as "99003"
    And "Open" is clicked
    Then a window shows the file "Record 99003"
    When the window is closed
    And record 99003 is opened in ZCBS itself from the screen
    Then the new tab shows "Kerkstraat 1960, de kerk van Heemskerk"

  # ---- Nieuwe bestanden ----

  Scenario: Two pdf files all the way into ZCBS, with the programs running by themselves
    Given Jelly scanned "kerk.pdf, school.pdf"
    And Carla is logged in on the screen
    When the app is opened at "/collectie/archief"
    And the choice "Nieuwe bestanden in ZCBS zetten" is made
    Then the screen is titled "1. Controleer of alles klaarstaat"
    When "Alles in orde: verder met bestanden kiezen" is clicked
    Then the screen is titled "2. Bestanden kiezen"
    When "Alles aanvinken" is ticked
    And "Open kerk.pdf" is clicked
    Then a window shows the file "kerk.pdf"
    When the window is closed
    And "Verder" is clicked
    Then the screen is titled "3. Nummers"
    And "Eerste recordnummer" on the screen is "99068"
    And the screen says "99069"
    When "Geef de bestanden deze nummers en ga verder" is clicked
    And "Automatisch uitvoeren" is clicked
    Then the screen says "Dit heeft pdf2zcbs.exe gemaakt" within a minute
    When "Bekijk log" is clicked
    Then a window shows the file "Log van pdf2zcbs.exe"
    And the window shows the text "EXIT code=0"
    When the window is closed
    And "Ja, goed: verder met images.exe" is clicked
    And "Automatisch uitvoeren" is clicked
    Then the screen says "Dit heeft images.exe gemaakt" within a minute
    And ZCBS has a record 99068 that still has to be described
    When "Ja, goed: verder met de upload" is clicked
    And "Uitvoeren" is clicked
    Then the screen is titled "7. "
    When "Uitvoeren" is clicked
    Then the screen is titled "Klaar"
    And the screen says "Recent toegevoegd bij Records aanpassen"
    And the backup has the pdf and the image of record 99068
    When the page is reloaded
    Then the screen is titled "Klaar"
    When "Recent toegevoegd bij Records aanpassen" is clicked
    Then the screen says "Nog niet beschreven"
    When "Bewerk 99068" is clicked
    And the title of the record is changed to "De kerk" on the screen
    And "Opslaan" is clicked
    Then the screen says "Volgende om te beschrijven: 99069"
    When the app is opened at "/collectie/archief/nieuw"
    And "Nieuwe bestanden verwerken" is clicked
    Then the screen is titled "1. Controleer of alles klaarstaat"
    When "Alles in orde: verder met bestanden kiezen" is clicked
    Then "kerk.pdf" cannot be ticked
    And the screen says "Al verwerkt: 99068"

  Scenario: Running images step by step and giving the answers yourself
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    And Carla has given the numbers on the screen
    When "Handmatig uitvoeren" is clicked
    Then the screen says "Dit heeft pdf2zcbs.exe gemaakt" within a minute
    When "Ja, goed: verder met images.exe" is clicked
    And "Handmatig uitvoeren" is clicked
    Then the program output shows "indien OK"
    When images is answered by hand on the screen
    Then the screen says "Dit heeft images.exe gemaakt" within a minute
    And ZCBS has a record 99068 that still has to be described
    And the server has the images of record 99068

  Scenario: Following the choices from the start
    Given Carla is logged in on the screen
    When the collection "archief" is chosen on the screen
    And the choice "ZCBS records aanpassen" is made
    Then the screen is titled "Records aanpassen"
    And the browser is at "/collectie/archief/records"

  Scenario: Reloading while a program waits for an answer, and stopping it
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    And Carla has given the numbers on the screen
    When "Automatisch uitvoeren" is clicked
    And "Ja, goed: verder met images.exe" is clicked
    And "Handmatig uitvoeren" is clicked
    Then the program output shows "indien OK"
    When the page is reloaded
    Then the program output shows "indien OK"
    When "Stop programma" is clicked
    Then "Opnieuw" can be clicked within a minute
    And the server has no files of record 99068

  Scenario: A step that fails shows why and can be done again
    Given Jelly scanned "kapot.pdf"
    And Carla has chosen "kapot.pdf" of Jelly on the screen
    And Carla has given the numbers on the screen
    When "Automatisch uitvoeren" is clicked
    Then "Opnieuw" can be clicked within a minute
    And the screen says "99068"
    When "Bekijk log" is clicked
    Then a window shows the file "Log van pdf2zcbs.exe"
    And the window shows the text "EXIT code=0"
    When the window is closed
    And "Opnieuw" is clicked
    Then "Automatisch uitvoeren" can be clicked within a minute

  Scenario: A file that is not really an image cannot go to images
    Given Ria scanned "kapot.jpg"
    And Carla has chosen "kapot.jpg" of Ria on the screen
    When "Geef de bestanden deze nummers en ga verder" is clicked
    Then the screen says "kapot.jpg is geen geldige afbeelding"
    And "Automatisch uitvoeren" cannot be clicked

  Scenario: Looking back at an earlier step changes nothing
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    When "Bekijk Bestanden kiezen" is clicked
    Then the screen is titled "2. Bestanden kiezen"
    And the screen says "Alleen bekijken"
    And the screen says "kerk.pdf"
    When "Terug naar de huidige stap" is clicked
    Then the screen is titled "3. Nummers"
    When Carla has given the numbers on the screen
    And "Automatisch uitvoeren" is clicked
    Then the screen says "Dit heeft pdf2zcbs.exe gemaakt" within a minute
    When "Ja, goed: verder met images.exe" is clicked
    And "Bekijk pdf2zcbs" is clicked
    Then the screen is titled "4. pdf2zcbs"
    And the screen says "Dit heeft pdf2zcbs.exe gemaakt"
    And the screen does not say "Ja, goed"
    When "Bekijk Bestanden kiezen" is clicked
    Then the screen is titled "2. Bestanden kiezen"
    And the screen says "kerk.pdf"
    And the screen does not say "99068.pdf"
    And "Keuze aanpassen" cannot be found
    When "Bekijk Nummers" is clicked
    Then the screen is titled "3. Nummers"
    And the screen says "99068.pdf"
    When "Bekijk Nummers" is clicked
    Then the screen is titled "3. Nummers"
    When "Ga naar images.exe" is clicked
    Then the screen says "Start images.exe"

  Scenario: Going back from the numbers to change the chosen files
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    When "Bekijk Bestanden kiezen" is clicked
    And "Keuze aanpassen" is clicked
    Then the screen is titled "2. Bestanden kiezen"
    And the screen says "1 bestand stond al klaar en doet mee:"

  Scenario: Choosing several files with shift, and nothing means no way on
    Given Jelly scanned "a.pdf, b.pdf, c.pdf"
    And Carla is logged in on the screen
    When the app is opened at "/collectie/archief/nieuw"
    And "Alles in orde: verder met bestanden kiezen" is clicked
    Then "Verder" cannot be clicked
    When "a.pdf" is ticked
    And "c.pdf" is ticked with shift
    Then "b.pdf" is ticked on the screen
    And the screen says "3 aangevinkt"

  Scenario: Numbers that are not free are refused, and a free range can be chosen
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    When "Eerste recordnummer" is filled in as "99003"
    And "Geef de bestanden deze nummers en ga verder" is clicked
    Then the screen says "Recordnummer 99003 is niet vrij in ZCBS"
    And the screen is titled "3. Nummers"
    When "Begin hier" is clicked
    Then "Eerste recordnummer" on the screen is "00001"

  Scenario: Starting over goes back to the checks
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    When "Opnieuw beginnen" is clicked
    Then the screen is titled "1. Controleer of alles klaarstaat"
    And the originals "kerk.pdf" of Jelly are still there

  Scenario: Leftovers of an earlier time are cleared on the screen
    Given Carla left the file "oud.pdf" in the folder of pdf2zcbs
    And Carla is logged in on the screen
    When the app is opened at "/collectie/archief/nieuw"
    Then "Los eerst de rode punten op" cannot be clicked
    When "Verplaats ze naar een map met resten" is clicked
    Then the screen says "Alles in orde: verder met bestanden kiezen"
    And the file "oud.pdf" is gone from the folder of pdf2zcbs of Carla

  # ---- Beheer ----

  Scenario: The settings ask for the administrator and then open
    When the app is opened at "/instellingen"
    Then the screen is titled "Inloggen als beheerder"
    When the admin logs in on the screen with the right password
    Then the screen is titled "Instellingen"

  @wijzigt-instellingen
  Scenario: Changing a setting and testing the ftp connection
    When the app is opened at "/instellingen"
    And the admin logs in on the screen with the right password
    And "Map voor resten van een eerdere run" is filled in as "/tmp/resten-op-het-scherm"
    And "Opslaan" is clicked
    Then the screen says "Instellingen opgeslagen"
    When the page is reloaded
    Then "Map voor resten van een eerdere run" on the screen is "/tmp/resten-op-het-scherm"
    When "Test verbinding" is clicked
    Then the screen says "Verbonden met"

  Scenario: Sessions: who is busy, and stopping a flow
    Given Carla is logged in
    And Jelly scanned "kerk.pdf"
    And Carla starts adding new files
    When the app is opened at "/instellingen/sessies"
    And the admin logs in on the screen with the right password
    Then the screen says "Bezig met nieuwe bestanden"
    And the screen mentions the account of Carla
    When the flow of Carla is stopped on the screen
    Then no flow of Carla is on the screen

  Scenario: The version and the new build on GitHub
    When the app is opened at "/instellingen/versie"
    And the admin logs in on the screen with the right password
    And "Controleer nu" is clicked
    Then the screen says "Build 99999"
    And "Nu bijwerken" cannot be clicked

  Scenario: The logs of the programs
    Given Jelly scanned "kerk.pdf"
    And Carla has chosen "kerk.pdf" of Jelly on the screen
    And Carla has given the numbers on the screen
    When "Automatisch uitvoeren" is clicked
    Then the screen says "Dit heeft pdf2zcbs.exe gemaakt" within a minute
    When the app is opened at "/instellingen/logs"
    And the admin logs in on the screen with the right password
    Then the screen says "Klaar (exitcode 0)"
    When "Toon" is clicked
    Then the screen says "Log:"
    When "Alle logs wissen" is clicked
    Then the screen says "Nog geen runs"

  Scenario: pdf2zcbs gets stuck on a helper program
    Given Jelly scanned "vastloper.pdf"
    And Carla has chosen "vastloper.pdf" of Jelly on the screen
    And Carla has given the numbers on the screen
    When "Automatisch uitvoeren" is clicked
    Then the screen says "misschien vastgelopen" within a minute
    And the screen says "liep vast op i_view32" within a minute
    And the screen says "Verwerking: 99068.pdf"

  Scenario: The menu of the administrator
    When the app is opened at "/beheer"
    And the admin logs in on the screen with the right password
    Then the screen is titled "Je bent ingelogd als beheerder"
    And the screen says "Sessies"
    When "Uitloggen als beheerder" is clicked
    Then the screen is titled "Welkom bij ZcbsServer"
