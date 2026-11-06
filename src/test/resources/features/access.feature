Feature: Logging in with the ZCBS account
  Everyone logs in with their name and password from ZCBS. The app has no accounts of its own.

  Scenario: Someone with rights in ZCBS logs in and chooses the archive
    Given Carla may edit records in ZCBS
    When Carla logs in
    Then Carla can choose the collection "archief"
    And Carla cannot choose the collection "artikelen"
    And the workflow log hears of a successful login by Carla
    When Carla chooses the collection "archief"
    Then Carla can work in the collection "archief"

  Scenario: A wrong password does not get you in
    Given Carla may edit records in ZCBS
    When Carla logs in with the password "verkeerd"
    Then Carla sees "Naam of wachtwoord niet herkend"
    And Carla is not logged in
    And the workflow log hears of a failed login by Carla

  Scenario Outline: Only logged-in users get the data of a collection
    When a visitor asks for "<address>"
    Then the visitor is told to log in

    Examples:
      | address                           |
      | /api/collections/archief/flow     |
      | /api/collections/archief/records  |

  Scenario: A collection without rights stays closed
    Given Carla is logged in
    When Carla chooses the collection "artikelen"
    Then Carla is refused with "no-rights"

  Scenario: Logging out
    Given Carla is logged in
    When Carla logs out
    Then Carla is not logged in

  Scenario: After a restart of the app the browser logs in again by itself
    Given Carla is logged in
    When Carla comes back after a restart of the app
    Then Carla can choose the collection "archief"
    And the workflow log hears that Carla was logged in again from the cookie
