# Setup Instructions

1. Download source and compile mod
2. Install the built jar into the mods folder
3. Place mods in the following directories:
   1. `servermods/` - Mods that should be sent to the client and server
   2. `clientmods/` - Mods that should only be sent to the client
   3. `servermods/override/` - Any files (i.e. config files, kubejs, etc.) that should also be sent to the client, relative to `.minecraft`
   4. `mods/` - Any mods that should be on the server _only_
4. Start up server

# Client Instructions
1. Install built jar into `mods/`
2. Start the client up to generate the certificate signing request
3. Sign the request
   1. `java -jar utils/signer.jar cacert.pem ca.key serverrequest.csr`
   2. This generates a cert called `<UUID>.pem`
4. Place the signed certificate in `servermods/servercert.pem`
5. Update `servermods/serverpacklocator.toml` to point to the server endpoint
6. Launch modpack

---

# Notes
* Files can be excluded from the overrides folder by adding them to `.minecraft/locator_ignored.txt`
  * Files that are ignoed will be downloaded if they do not exist on the client, but will not be overwritten
  * `config/*-client.toml` and `defaultconfigs/*-client.toml` are ignored by default
