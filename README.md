<p align="center">
  <img src="https://pac4j.github.io/pac4j/img/logo-vertx.png" width="300" />
</p>

This `vertx-pac4j-demo` project is a Vertx web application to test the [vertx-pac4j](https://github.com/pac4j/vertx-pac4j) security library with various authentication mechanisms: Facebook, Twitter, form, basic auth, CAS, SAML, OpenID Connect, JWT...

## Start & test

Build the project and launch the Vertx web app on [http://localhost:8080](http://localhost:8080):

    cd vertx-pac4j-demo
    mvn clean package
    java -jar target/vertx-pac4j-demo-4.0.0-SNAPSHOT-fat.jar

To test, you can call a protected url by clicking on the "Protected url by **xxx**" link, which will start the authentication process with the **xxx** provider.
