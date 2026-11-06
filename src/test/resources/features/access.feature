Feature: Logging in with the ZCBS account
  Everyone logs in with their name and password from ZCBS. The app has no accounts of its own.

  Scenario: Someone with rights in ZCBS logs in and chooses the archive
    Given Carla may edit records in ZCBS
    When Carla logs in
    Then Carla can choose the collection "archief"
    And Carla cannot choose the collection "artikelen"
    And the workflow log hears of a successful login by Carla
    When Carla chooses the collection "archief"
    Then Carla sees "Nieuwe bestanden"
    And Carla sees "records aanpassen"

  Scenario: A wrong password does not get you in
    Given Carla may edit records in ZCBS
    When Carla logs in with the password "verkeerd"
    Then Carla sees "Naam of wachtwoord niet herkend"
    And Carla is back on the start page
    And the workflow log hears of a failed login by Carla

  Scenario Outline: Pages for logged-in users send visitors to the start page
    When a visitor opens "<page>"
    Then the visitor is sent to "/"

    Examples:
      | page       |
      | /collectie |
      | /nieuw     |
      | /records   |

  Scenario: Logging out
    Given Carla is logged in
    When Carla logs out
    Then Carla is back on the start page

  Scenario: After a restart of the app the browser logs in again by itself
    Given Carla is logged in
    When Carla comes back after a restart of the app
    Then Carla can choose the collection "archief"
    And the workflow log hears that Carla was logged in again from the cookie
