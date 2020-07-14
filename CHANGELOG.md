Changelog
===

## v1.8 (tbd)
* change to improve ordering of tag values for parameter
* add option to revere ordering
* add default credential used for the default registry
* change to move registry, credential and reverseOrder option into advanced configuration
* fix JobDSL API breaking by requiring new optional properties for parameter creation (regression from v1.6)

## v1.7 (Jul 10, 2020)
* add support for basic authorization type (for repositories like AWS ECR and Registry (self hosted))

## v1.6 (Jun 25, 2020)
* Add support for default value
* Add additional envVar export to get imageTag without image name

## v1.5 (Jun 15, 2020)
* Fix pagination in HTML for display

## v1.4 (Jun 01, 2020)
* Compatibility with Pipeline

## v1.3 (Jun 01, 2020)
* For compatibility with OAuth 2.0, we will also accept token under the name access_token

## v1.2 (Dec 30, 2019)
* Possibility to set a default registry in global configuration

## v1.1.1 (Dec 19, 2019)
* Change pom.xml groupId to org.jenkins-ci.plugins

## v1.1 (Dec 19, 2019)
* Add unirest error inteceptor for handling hostname and connection error

## v1.0 (Dec 17, 2019)
* Initial Release