# bitcoin-wallet

##Server start
Simple way to start our server is: `./gradlew bootRun`
App can work with H2 (embedded by default, but can work with file) or with in-memory repo.
see `spring.profiles.active` propery.

##Working with app
Add new wallet entry:
`curl -v --header "Content-Type: application/json" \
  --request POST \
  --data '{"amount":"10.05","datetime":"2019-10-05T14:45:05Z"}' \
  http://localhost:8080/api/wallet/add-entry`

Get balance for a given period:
`curl -v --header "Content-Type: application/json" \
  --request POST \
  --data '{"from":"2019-10-05T14:45:05Z","to":"2019-10-05T18:45:05Z"}' \
  http://localhost:8080/api/wallet/balance`

##Stack
Spring Boot, liquibase for migrations, lombok to reduce boiler-plate.
embedded H2 as database


##Possible improvements
Though this is a working code, there are some possible improvements
- Use embedded-postgres/local postgres. H2 is okay, but we probably should switch to postgres.
https://github.com/yandex-qatools/postgresql-embedded

- RateLimiting. It should be done the container/balancer level.
But if we have no othe options - we can add simple ratelimiting via request inteception.