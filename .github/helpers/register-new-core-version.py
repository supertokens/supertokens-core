import json
import os

import http.client

def register_core_version(supertokens_api_key, core_version, plugin_interface_array, core_driver_array):
	print("Core Version: ", core_version)
	print("Plugin Interface Array: ", plugin_interface_array)
	print("Core Driver Array: ", core_driver_array)

	conn = http.client.HTTPSConnection("api.supertokens.io")

	payload = {
		"password": supertokens_api_key,
		"planType": "FREE",
		"version": core_version,
		"pluginInterfaces": plugin_interface_array,
		"coreDriverInterfaces": core_driver_array
	}
	
	headers = {
		'Content-Type': 'application/json',
		'api-version': '0'
	}
	
	conn.request("PUT", "/0/core", json.dumps(payload), headers)
	response = conn.getresponse()
	
	if response.status != 200:
		print(f"failed core PUT API status code: {response.status}. Exiting!")
		exit(1)
	
	conn.close()


def read_core_version():
	with open('build.gradle', 'r') as file:
		for line in file:
			if 'version =' in line:
				return line.split('=')[1].strip().strip("'\"")
	raise Exception("Could not find version in build.gradle")

core_version = read_core_version()

with open('pluginInterfaceSupported.json', 'r') as fd:
	plugin_interface_array = json.load(fd)['versions']

with open('coreDriverInterfaceSupported.json', 'r') as fd:
	core_driver_array = json.load(fd)['versions']

register_core_version(
	supertokens_api_key=os.environ.get("SUPERTOKENS_API_KEY"),
	core_version=core_version,
	plugin_interface_array=plugin_interface_array,
	core_driver_array=core_driver_array
)