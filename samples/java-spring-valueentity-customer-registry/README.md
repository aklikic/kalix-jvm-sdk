# Value Entity Customer Registry

## Designing

To understand the Kalix concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Value Entity and View components.
To understand more about these components, see [Developing services](https://docs.kalix.io/services/)
and in particular the [Java section](https://docs.kalix.io/java/)

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

When running a Kalix service locally, we need to have its companion Kalix Runtime running alongside it.

To start your service locally, run:

```shell
mvn kalix:runAll
```

This command will start your Kalix service and a companion Kalix Runtime as configured in [docker-compose.yml](./docker-compose.yml) file.

## Exercise the service

With both the Kalix Runtime and your service running, once you have defined endpoints they should be available at `http://localhost:9000`.

* Create a customer with:

```shell
curl localhost:9000/customer/one/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"customerId":"one","email":"test@example.com","name":"Test Testsson","address":{"street":"Teststreet 25","city":"Testcity"}}'
```

* Retrieve the customer:

```shell
curl localhost:9000/customer/one
```

* Query by name with a wrapped result:

```shell
curl localhost:9000/wrapped/by_name/Test%20Testsson
```

* Query by name with a response using a summary:

```shell
curl localhost:9000/summary/by_name/Test%20Testsson
```

## Deploying

To deploy your service, install the `kalix` CLI as documented in
[Setting up a local development environment](https://docs.kalix.io/setting-up/)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Kalix, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `kalix` CLI.
