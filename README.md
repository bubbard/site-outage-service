# Site-outage-service

Created a simple spring app that runs without an embedded server. The reason Spring was chosen was that it already provides:
- json ser/deserialisation
- dependency injection
- logging
- configuration
 
Additionally, since the scope was very open, developing in spring allows it to be easily extended: event driven (with kafka listener), have it's own rest api, or be a simple cli command.

## Dependencies
- Maven 3.6.3
- Java 17
- Spring Boot 3.0.5
- Junit 5

## Running
JAVA_HOME system env must be set to jdk17 or later.

I've included maven wrapper, so you shouldn't need to install maven on your local machine first.

**From the project root**, first run:

``./mvnw clean install``

This will build the project as well as running all tests.

If you want to run the steps individually then:

``./mvnw clean test``

Will run the tests. And to just run the build you can use:

``./mvnw -DskipTests=true clean package``


After building you should see a target folder with the jar.

You should be able to run either (Replacing the ``<REPLACE_ME>`` placeholder with a valid api key):

``./mvnw spring-boot:run "-Dspring-boot.run.arguments=--kraken.rest.apiKey=<REPLACE_ME>"``

or:

``java -jar .\target\site-outage-service-0.0.1-SNAPSHOT.jar --kraken.rest.apiKey=<REPLACE_ME>``


## Considerations

- Service layer (OutageDetailService)
  - Contains business logic of filtering and matching ids. Developed in a functional-ish way (no side-effects in methods).
  - Mapping devices by id meant that we didn't have to check every outage id against every device id.
    - Because of this mapping then defensively had to add logic if there were any duplicate ids for different devices on a Site.
    - OutageDetailService#getDeviceById
  - Leveraged Streams and Optionals for readability.
- Web Layer (KrakenWebClient)
  - While we're only using synchronous http calls for this task, with WebClient we can easily extend to support more use-cases. WebClient is also recomended instead of RestTemplate (which is now in maintenance mode).
  - Additionally, WebClient supports retry logic in a really simple way. Allowing for this service to be resilient against occasional 500 responses.
    
  ``Retry.backoff(maxRetries, Duration.ofSeconds(minBackoff))``
  - Backoff retry policy was selected since we want to give the server a chance to recover. This policy will add more time between retries exponentially to give the most chance of successful completion.
  - The max retries and min back off time (in seconds) are configurable. So in a deployed environment we wouldn't have to rebuild the app if the values needed to be changed.
  - With custom exceptions we can easily filter for 5xx responses only (there is little point retrying for 4xx since they are not likely to resolve themselves):
    
  ``
      .filter(KrakenServerException.class::isInstance)
      ``
- Dtos
  - Created immutable record pojos for each expected response/request data structure. Immutability Removes uncertainty and readability compared to if objects were mutating as they're passed around methods.
    - Example of where this came in handy was dealing with the time comparisons. Storing the begin/end as ZonedDateTime would ultimately change the string value (adding on an explicit time zone), while the expectation was that the time values should not change. For this reason I kept them all as strings and only when it came to the comparison would I use ZonedDateTime functionality.
  
  ``public record Outage(String id, String begin, String end)``
  - Ignoring any additional fields that may be added in future as we don't need them for our logic (for the current requirements).
- Api Key
  - Since this artefact isn't getting deployed, I couldn't use normal vault (hashicorp) for secrets so instead it can either be added via config (but shouldn't be commited) or be added as a cli argument.
- Configuration
  - Made most fields that could be extracted configurable as the decouple them a bit more from the implementation. I'm not expecting them to change but just allows for a separation of concerns. For example, without knowing the business reason for filtering out some outages based on time then having it defined in config allows the implementation to be agnostic.
  - The endpoints aren't (currently) configurable since they are less likely to change (well at least would hopefully change in a backwards compatible manor).
- Testing
  - The service is unit tested by mocking out the webclient. Allows for isolated testing of the business logic through the public methods. Started with basic time edge cases tests and also included scenario with the given example jsons.
  - Web client tested with a mock web server. Enables testing of retry and error handling with expected exceptions. Additionally, allows to ensure are pojos records are deserialising/serialising as expected.
