MAIN_SOURCES := $(shell find src/main/java -name '*.java' -print)
TEST_SOURCES := $(shell find src/test/java -name '*.java' -print)
MAIN_CLASS := dev.datnguyen.missionscheduler.MissionScheduler

.PHONY: build compile test clean

build: compile
	jar --create --file build/mission-scheduler.jar --main-class $(MAIN_CLASS) -C build/classes .

compile:
	mkdir -p build/classes
	javac --release 21 -Xlint:all -Werror -d build/classes $(MAIN_SOURCES)

test: compile
	mkdir -p build/test-classes
	javac --release 21 -Xlint:all -Werror -cp build/classes -d build/test-classes $(TEST_SOURCES)
	java -ea -cp build/classes:build/test-classes dev.datnguyen.missionscheduler.MissionSchedulerTests

clean:
	rm -rf build
