# Tangem
Tangem is a card that can be used to sign Iroha transactions securely. It supports `ed25519` alongside with `SHA2-512`. By design, Iroha uses `SHA3-512` as a hashing algorithm by default, so it's necessary to run a `SHA2-512` based Iroha. Please, ask Iroha maintainers for more details. At the moment, Tangem has an SDK for two mobile platforms: [Android](https://github.com/Tangem/tangem-sdk-android) and [IOS](https://github.com/Tangem/tangem-sdk-ios). The desktop library is coming soon. 
## Possible drawbacks 
### Private key restoring
It's impossible to restore your private key. If you have lost your card, the key is lost forever. Fortunately, Iroha supports MultiSig, so it's possible to buy 2 Tangem cards(the main card and the backup card), create an Iroha account with 2 public keys from the cards and set account's quorum to 1 out of 2. If you have lost the main card, you will still be able to sign transactions with the backup card.
### No pin code protection
Even though Tangem cards support pin code protection, it's not possible to use this functionality in SDK yet.

## Demo app
It is quite easy to test Tangem cards with the [demo app](https://github.com/dolgopolovwork/TangemTest/tree/master/app) for Android. 
1) First, you have to run Iroha node on your local machine using  `docker-compose -f deploy/docker-compose.yml up`. 
2) Then, build and run the app on your device. Or you may just download and install the [apk](https://github.com/dolgopolovwork/TangemTest/tree/master/app-debug.apk).
3) And follow instructions from the `instructions.md` file

The app has two buttons: "scan" and "sign". You have to scan your card to get its public key first. After that, you can sign and send transactions to Iroha. The id of the card that is used to sign transactions is `CB28 0000 0000 5309`.

## Problems and Solutions
- Error `ERROR: for sha2-iroha  Cannot start service sha2-iroha: OCI runtime create failed: container_linux.go:345: starting container process caused "exec: \"/opt/iroha_data/entrypoint.sh\": permission denied": unknown` can be faced in starting docker containers for the project. To solve that, make `/deploy/iroha/entrypoint.sh` executable:
```
chmod +x /deploy/iroha/entrypoint.sh
```

## SDK

Tangem cards can be used for iOS and Android. SDK for these platforms can be foun [here](https://github.com/Tangem)
