Feature: The REST API
  The frontend only talks to the app through this API, so it behaves the same everywhere.

  Scenario: An error has a fixed form
    When a visitor asks for "/api/collections/archief/flow"
    Then the visitor gets a problem with the code "not-logged-in" and a message to show

  Scenario: An address that does not exist is an error in the same form
    When a visitor asks for "/api/bestaat-niet"
    Then the visitor gets a problem with the code "not-found" and a message to show

  Scenario: The API describes itself
    When a visitor asks for "/api/openapi.json"
    Then the description of the API has "/api/collections/{collection}/records/{number}/edit"

  Scenario: The password never leaves the settings
    Given the administrator is logged in
    When the administrator opens the settings
    Then the administrator sees "ftpPasswordSet"
    And the settings do not contain the ftp password

  Scenario: Reading a record does not lock it
    Given Carla is logged in
    And Bob is logged in
    When Bob reads the scan of record 99003
    And Carla opens record 99003 for editing
    Then Carla sees the title "Kerkstraat 1960, de kerk van Heemskerk" in the form

  @openapi
  Scenario: The frontend was built against this API
    When a visitor asks for "/api/openapi.json"
    Then the description of the API is the same as the copy of the frontend
