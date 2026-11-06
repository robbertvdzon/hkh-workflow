Feature: Putting new files in ZCBS
  Carla chooses scans, gives them record numbers, runs pdf2zcbs and images, uploads the rest and ends with a backup. The programs talk to the ftp server of ZCBS, as they really do.

  Background:
    Given Carla is logged in
    And Jelly scanned "kerk.pdf, school.pdf"

  Scenario: Two pdf files all the way into ZCBS
    When Carla opens the page for new files
    Then all checks are green for Carla
    When Carla starts adding new files
    Then Carla has reached step 2
    When Carla picks "kerk.pdf, school.pdf" from Jelly
    Then Carla has reached step 3
    And Carla is offered 99068 as the first number
    When Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    Then Carla sees "Dit heeft pdf2zcbs.exe gemaakt"
    And Carla can view the tekst result of record 99068
    When Carla approves the result
    And Carla runs images
    Then Carla sees "Dit heeft images.exe gemaakt"
    And ZCBS has a record 99068 that still has to be described
    And ZCBS has a record 99069 that still has to be described
    And the server has the images of record 99068
    When Carla approves the result
    And Carla runs the upload
    Then the server has the pdf, the text and the images of record 99068
    And the server has the pdf, the text and the images of record 99069
    When Carla runs the backup
    Then Carla sees "op het archief"
    And the backup has the pdf and the image of record 99068
    And the backup has the pdf and the image of record 99069
    And the originals "kerk.pdf, school.pdf" of Jelly are still there
    And nothing of Carla is left in the work folders

  Scenario: Added records show up in the recent list, and a processed scan cannot be chosen again
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla finishes all the steps
    Then Carla sees record 99068 in the own recent list
    And Carla sees record 99068 in the to-describe recent list
    When Carla opens record 99068 for editing
    And Carla saves the title "De kerk"
    Then Carla sees record 99068 in the recent list with title "De kerk"
    And Carla does not see record 99068 in the to-describe recent list
    When Carla opens the page for new files
    And Carla starts over
    And Carla starts adding new files
    Then Carla cannot pick "kerk.pdf" from Jelly any more

  Scenario: Somebody else does not see my records in the own list, only in the complete list
    Given Bob has added "kerk.pdf" from Jelly up to the end
    Then Carla does not see record 99068 in the own recent list
    And Carla sees record 99068 in the complete recent list

  Scenario: An image goes straight to images, without pdf2zcbs and without upload
    Given Ria scanned "foto.jpg"
    When Carla starts adding new files
    And Carla picks "foto.jpg" from Ria
    And Carla accepts the offered numbers
    And Carla finishes all the steps
    Then ZCBS has a record 99068 that still has to be described
    And the server has the images of record 99068
    And the server has no pdf or text of record 99068
    And the backup has the image of record 99068

  Scenario: Choosing nothing does not go on
    When Carla starts adding new files
    And Carla picks nothing from Jelly
    Then Carla has reached step 2
    And Carla sees "Vink minstens één bestand aan"

  Scenario: Numbers that are not free are refused
    When Carla starts adding new files
    And Carla picks "kerk.pdf, school.pdf" from Jelly
    And Carla chooses 99002 as the first number
    Then Carla has reached step 3
    And Carla sees "passen niet vanaf 99002"
    And ZCBS has no record 99002

  Scenario: Someone who may not add records is stopped at the first step
    Given Rob may only read records in ZCBS
    When Rob logs in
    And Rob opens the page for new files
    Then the check "vrije recordnummers" is red for Rob
    And Rob sees "Los eerst de rode punten op"

  Scenario: pdf2zcbs reports success but did not make the files
    Given Jelly scanned "kapot.pdf"
    When Carla starts adding new files
    And Carla picks "kapot.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    Then Carla sees "99068"
    And Carla does not see "Dit heeft pdf2zcbs.exe gemaakt"
    And ZCBS has no record 99068

  Scenario: Somebody used one of my numbers in the meantime: images does not start
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    And Carla approves the result
    And someone else already used number 99068 in ZCBS
    And Carla runs images
    Then Carla sees "99068"
    And Carla does not see "Dit heeft images.exe gemaakt"
    And the server has no files of record 99068

  Scenario: The upload never overwrites a file on the server
    Given the server already has the file "archief/pdf/99068.pdf"
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    And Carla approves the result
    And Carla runs images
    And Carla approves the result
    And Carla runs the upload
    Then Carla sees "Bestaat al op de server"
    And Carla sees "99068.pdf"

  Scenario: Starting over leaves the originals alone
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla starts over
    Then the originals "kerk.pdf, school.pdf" of Jelly are still there
    And nothing of Carla is left in the work folders
    And ZCBS has no record 99068

  Scenario: Two people work next to each other
    Given Bob is logged in
    And Ria scanned "bob.pdf"
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Bob starts adding new files
    And Bob picks "bob.pdf" from Ria
    Then Carla has reached step 3
    And Bob has reached step 3
    And Bob sees "1"

  Scenario: images run by hand, answering the questions in the console
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    And Carla approves the result
    And Carla runs images by hand
    Then Carla sees "Dit heeft images.exe gemaakt"
    And ZCBS has a record 99068 that still has to be described
    And the server has the images of record 99068

  Scenario: A png becomes a jpg on the way
    Given Ria scanned "plattegrond.png"
    When Carla starts adding new files
    And Carla picks "plattegrond.png" from Ria
    And Carla accepts the offered numbers
    And Carla finishes all the steps
    Then the server has the images of record 99068
    And the originals "plattegrond.png" of Ria are still there

  Scenario: Looking at a scan before choosing it
    When Carla starts adding new files
    Then Carla can look at "kerk.pdf" from Jelly before choosing it

  Scenario: Only results of this run can be viewed
    When Carla starts adding new files
    And Carla picks "kerk.pdf" from Jelly
    And Carla accepts the offered numbers
    And Carla runs pdf2zcbs
    Then Carla can view the tekst result of record 99068
    And Carla cannot view the tekst result of record 99003

  Scenario: Leftovers of an earlier time are moved away first
    Given Carla left the file "oud.pdf" in the folder of pdf2zcbs
    When Carla opens the page for new files
    Then the check "werk van een eerdere keer" is red for Carla
    And Carla sees "Los eerst de rode punten op"
    When Carla moves the leftovers away
    Then all checks are green for Carla
    And the file "oud.pdf" is gone from the folder of pdf2zcbs of Carla

  Scenario: Leftovers of an earlier time can also be deleted
    Given Carla left the file "oud.pdf" in the folder of pdf2zcbs
    When Carla opens the page for new files
    And Carla deletes the leftovers
    Then all checks are green for Carla
    And the file "oud.pdf" is gone from the folder of pdf2zcbs of Carla
