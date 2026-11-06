@screens
Feature: The screens of the frontend
  The other features go through the API. These open a real browser, to see that the screens use that API correctly.

  Scenario: Logging in and choosing a collection
    Given Carla may edit records in ZCBS
    When the app is opened at "/app/"
    Then the screen is titled "Welkom bij ZcbsServer"
    When Carla logs in on the screen
    Then the screen is titled "Met welke collectie wil je werken?"
    And the screen says "Rechten in ZCBS"
    And the collection "archief" can be chosen on the screen
    And the collection "artikelen" cannot be chosen on the screen
    When the collection "archief" is chosen on the screen
    Then the screen is titled "Collectie archief"
    And the browser is at "/app/collectie/archief"

  Scenario: A wrong password shows the message from the API
    Given Carla may edit records in ZCBS
    When the app is opened at "/app/"
    And Carla logs in on the screen with the password "verkeerd"
    Then the screen says "Naam of wachtwoord niet herkend"
    And the screen is titled "Welkom bij ZcbsServer"

  Scenario: Reloading a screen keeps you there
    Given Carla is logged in on the screen
    When the app is opened at "/app/collectie/archief"
    And the page is reloaded
    Then the screen is titled "Collectie archief"

  Scenario: A link while not logged in returns there after logging in
    Given Carla may edit records in ZCBS
    When the app is opened at "/app/collectie/archief"
    Then the screen says "Log eerst in bij ZCBS."
    When Carla logs in on the screen
    Then the screen is titled "Collectie archief"
    And the browser is at "/app/collectie/archief"

  Scenario: A collection without rights goes back to the choice
    Given Carla is logged in on the screen
    When the app is opened at "/app/collectie/artikelen"
    Then the screen is titled "Met welke collectie wil je werken?"

  Scenario: Logging out on the screen
    Given Carla is logged in on the screen
    When "Uitloggen" is clicked
    Then the screen is titled "Welkom bij ZcbsServer"
    When the page is reloaded
    Then the screen is titled "Welkom bij ZcbsServer"

  Scenario: The admin logs in and out on the screen
    When the app is opened at "/app/beheer"
    And the admin logs in on the screen with the password "verkeerd"
    Then the screen says "Dat wachtwoord klopt niet."
    When the admin logs in on the screen with the right password
    Then the screen is titled "Je bent ingelogd als beheerder"
    When "Uitloggen als beheerder" is clicked
    Then the screen is titled "Inloggen als beheerder"

  Scenario: A screen that does not exist says so
    When the app is opened at "/app/bestaat-niet"
    Then the screen is titled "Dit scherm bestaat niet"

  Scenario: The address without a slash also opens the app
    When the app is opened at "/app"
    Then the screen is titled "Welkom bij ZcbsServer"

  Scenario: The files of the frontend
    When the app is opened at "/app/"
    Then the frontend is served with its own scripts
    And the file "/app/assets/bestaat-niet.js" does not exist
    And the file "/app/bestaat-niet.png" does not exist
