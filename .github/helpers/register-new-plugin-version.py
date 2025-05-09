import json
import os

import http.client

def register_plugin_version(supertokens_api_key, plugin_version, plugin_interface_array, plugin_name):
    print("Plugin Version: ", plugin_version)
    print("Plugin Interface Array: ", plugin_interface_array)
    print("Plugin Name: ", plugin_name)

    conn = http.client.HTTPSConnection("api.supertokens.io")

    payload = {
        "password": supertokens_api_key,
        "planType": "FREE",
        "version": plugin_version,
        "pluginInterfaces": plugin_interface_array,
        "name": plugin_name
    }

    headers = {
        'Content-Type': 'application/json',
        'api-version': '0'
    }

    conn.request("PUT", "/0/plugin", json.dumps(payload), headers)
    response = conn.getresponse()

    if response.status != 200:
        print(f"failed plugin PUT API status code: {response.status}. Exiting!")
        exit(1)

    conn.close()

def read_plugin_version():
	with open('build.gradle', 'r') as file:
		for line in file:
			if 'version =' in line:
				return line.split('=')[1].strip().strip("'\"")
	raise Exception("Could not find version in build.gradle")

plugin_version = read_plugin_version()

with open('pluginInterfaceSupported.json', 'r') as fd:
	plugin_interface_array = json.load(fd)['versions']

register_plugin_version(
	supertokens_api_key=os.environ.get("SUPERTOKENS_API_KEY"),
	plugin_version=plugin_version,
	plugin_interface_array=plugin_interface_array,
	plugin_name=os.environ.get("PLUGIN_NAME")
)
