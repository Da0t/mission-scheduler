.PHONY: build test run app clean
build:
	./mvnw package
test:
	./mvnw test
run:
	./mvnw spring-boot:run
app: run
clean:
	./mvnw clean
