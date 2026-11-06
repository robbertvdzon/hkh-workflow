Feature: The administrator
  The settings and the technical pages are only for the administrator, who logs in with the password from the configuration of the server.

  Scenario: The settings are closed without the administrator password
    When someone tries the administrator password "fout"
    Then the administrator sees "Dat wachtwoord klopt niet"
    And the settings are closed for this person

  Scenario: The administrator tests the ftp connection with the server
    Given the administrator is logged in
    When the administrator tests the ftp connection
    Then the administrator sees "Verbonden met"

  @wijzigt-instellingen
  Scenario: A changed setting is saved and reported to the workflow log
    Given the administrator is logged in
    When the administrator changes the folder for leftovers to "/tmp/resten-van-de-test"
    Then the settings show "/tmp/resten-van-de-test" as the folder for leftovers
    And the workflow log hears that the folder for leftovers changed to "/tmp/resten-van-de-test"

  Scenario: The administrator sees that a new version is available on GitHub
    Given the administrator is logged in
    When the administrator checks for a new version
    Then the administrator sees "99999"

  Scenario: The administrator sees who is busy and can stop a session
    Given Carla is logged in
    And Jelly scanned "kerk.pdf"
    And the administrator is logged in
    When Carla starts adding new files
    Then the administrator sees Carla among the sessions
    When the administrator stops the session of Carla
    Then the administrator does not see Carla among the sessions any more

  Scenario: The administrator can clear the logs of the programs
    Given the administrator is logged in
    When the administrator deletes all program logs
    Then there are no program logs left
