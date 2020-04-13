.PHONY: all clean plugin docker-install
.SUFFIXES:

version := $(shell grep '^    <version>.*</version>' pom.xml | cut -d '>' -f 2 | cut -d '<' -f 1)
plugin_name := gocd-riemann-notifier-$(version).jar

plugin_path := target/$(plugin_name)
install_path := docker/gocd-server/plugins/external/$(plugin_name)

all: plugin

clean:
	rm -rf target

$(plugin_path): pom.xml $(shell find src -type f)
	mvn install

plugin: $(plugin_path)

$(install_path): $(plugin_path)
	mkdir -p $$(dirname $@)
	cp $^ $@
	cd docker; docker-compose restart gocd-server

docker-install: $(install_path)
