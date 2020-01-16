package com.example.testtangem

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.tangem.CardManager
import com.tangem.commands.Card
import com.tangem.tangem_sdk_new.DefaultCardManagerDelegate
import com.tangem.tangem_sdk_new.NfcLifecycleObserver
import com.tangem.tangem_sdk_new.nfc.NfcManager
import com.tangem.tasks.ScanEvent
import com.tangem.tasks.TaskEvent
import iroha.protocol.Primitive
import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.*
import org.apache.xerces.impl.dv.xs.HexBinaryDV
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.spongycastle.util.encoders.Hex
import org.spongycastle.util.encoders.HexEncoder
import javax.xml.bind.DatatypeConverter
import javax.xml.bind.annotation.adapters.HexBinaryAdapter


private const val TAG = "Tangem test"

class MainActivity : AppCompatActivity() {

    private val managerAccountName = "manager"
    private val userAccountName = "test"
    private val domain = "tangem"
    private val userAtDomain = "%s@%s".format(userAccountName, domain)
    private val managerAtDomain = "%s@%s".format(managerAccountName, domain)

    private val nfcManager = NfcManager()
    private val cardManagerDelegate: DefaultCardManagerDelegate =
        DefaultCardManagerDelegate(nfcManager.reader)
    private val cardManager = CardManager(nfcManager.reader, cardManagerDelegate)
    private val userPublicKeys: ArrayList<String> = ArrayList()
    private var managerPublicKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcManager.setCurrentActivity(this)
        cardManagerDelegate.activity = this
        lifecycle.addObserver(NfcLifecycleObserver(nfcManager))
        val addNewUserCardButton: Button = findViewById(R.id.addNewUserCardButton)!!
        val scanButton: Button = findViewById(R.id.scanButton)!!
        val addCardButton: Button = findViewById(R.id.addCardButton)!!
        val signButton: Button = findViewById(R.id.signButton)!!
        val lostCardButton: Button = findViewById(R.id.lostCardButton)!!
        val scanManagerCardButton: Button = findViewById(R.id.addManagerCardButton)!!
        val irohaIpAddressEditText: EditText = findViewById(R.id.ipAddress)
        var card: Card? = null

        addNewUserCardButton.setOnClickListener { _ ->

        }

        scanManagerCardButton.setOnClickListener { _ ->
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                val managerCard: Card = (taskEvent.data as ScanEvent.OnReadEvent).card
                                managerPublicKey = Hex.toHexString(managerCard!!.walletPublicKey)
                            }
                        }
                    }
                }
            }
        }

        // For saving current session public keys in app
        addCardButton.setOnClickListener{_ ->
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                card = (taskEvent.data as ScanEvent.OnReadEvent).card
                                val publicKey: String = Hex.toHexString(card!!.walletPublicKey)
                                if (userPublicKeys.contains(publicKey)) {
                                    toast("Card already has been scanned. Add a second one.")
                                } else {
                                    userPublicKeys.add(publicKey)
                                }
                            }
                        }
                    }
                }
            }
        }

        // First, we have to scan the card to get its id and public key
        scanButton.setOnClickListener { _ ->
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                // Handle returned card data
                                card = (taskEvent.data as ScanEvent.OnReadEvent).card
                                if (!userPublicKeys.contains(Hex.toHexString(card!!.walletPublicKey))) {
                                    toast("Card is not in the list. Please, add it.")
                                }
                            }
                        }
                    }
                }
            }
        }

        lostCardButton.setOnClickListener{_ ->
            if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            }
            cardManager.scanCard { taskEvent ->
                when (taskEvent) {
                    is TaskEvent.Event -> {
                        when (taskEvent.data) {
                            is ScanEvent.OnReadEvent -> {
                                card = (taskEvent.data as ScanEvent.OnReadEvent).card
                                val publicKeyToRemove: String = userPublicKeys
                                    .filter { key -> !key.equals(Hex.toHexString(card!!.walletPublicKey)) }
                                    .first()

                                val unsignedTransaction = createRemoveSignatoryTransaction(Hex.decode(publicKeyToRemove))
                                cardManager.sign(
                                    arrayOf(unsignedTransaction.payload()),
                                    card!!.cardId
                                ) {
                                    when (it) {
                                        is TaskEvent.Completion -> {
                                            if (it.error != null) runOnUiThread {
                                                Log.e(TAG, it.error!!.message ?: "Error occurred")
                                                toast("Error occurred")
                                            }
                                        }
                                        is TaskEvent.Event -> runOnUiThread {
                                            val signature = formSignature(it.data.signature, card!!.walletPublicKey)
                                            val signedTx = unsignedTransaction.addSignature(signature).build()
                                            lostCardButton.isEnabled = false
                                            sendTransactionToIroha(
                                                irohaIpAddressEditText.text.toString(),
                                                signedTx,
                                                {
                                                    lostCardButton.isEnabled = true
                                                    toast("Transaction has been sent. Your lost card will be removed. Please, creare a new additional at Bank office")
                                                },
                                                {
                                                    lostCardButton.isEnabled = true
                                                    toast("Cannot send transaction")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        // Then, we create and sign a transaction
        signButton.setOnClickListener { _ ->
            if (card == null) {
                toast("Please, scan your card first")
                return@setOnClickListener
            } else if (irohaIpAddressEditText.text.toString().isEmpty()) {
                toast("Please, set Iroha node IP address")
                return@setOnClickListener
            }
            // Create tx
            val unsignedTransaction = createTransaction()
            // Sign it
            cardManager.sign(
                arrayOf(unsignedTransaction.payload()),
                card!!.cardId
            ) {
                when (it) {
                    is TaskEvent.Completion -> {
                        if (it.error != null) runOnUiThread {
                            Log.e(TAG, it.error!!.message ?: "Error occurred")
                            toast("Error occurred")
                        }
                    }
                    is TaskEvent.Event -> runOnUiThread {
                        val signature = formSignature(it.data.signature, card!!.walletPublicKey)
                        val signedTx = unsignedTransaction.addSignature(signature).build()
                        signButton.isEnabled = false
                        sendTransactionToIroha(
                            irohaIpAddressEditText.text.toString(),
                            signedTx,
                            {
                                signButton.isEnabled = true
                                toast("Transaction has been sent")
                            },
                            {
                                signButton.isEnabled = true
                                toast("Cannot send transaction")
                            })
                    }
                }
            }
        }
    }

    /**
     * Sends a transaction to Iroha node
     * @param irohaIPAddress - Iroha IP address
     * @param transaction - transaction to send
     * @param onSuccess - function that is executed if a given tx is successfully committed
     * @param onFail - function that is executed on error
     */
    private fun sendTransactionToIroha(
        irohaIPAddress: String,
        transaction: TransactionOuterClass.Transaction,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        //TODO this thing must be closed properly
        val iroha = IrohaAPI(irohaIPAddress, 50051)
        val irohaConsumer = IrohaConsumer(iroha)
        doAsync {
            irohaConsumer.send(transaction).fold({ uiThread { onSuccess() } },
                { ex ->
                    Log.e(TAG, "Cannot send transaction to Iroha", ex)
                    uiThread { onFail() }
                })
        }
    }

    /**
     * Creates a simple `GetSignatories` query
     * @return unsigned `RemoveSignatory` query
     */
    private fun createGetAllSignatoriesQuery(accountId: String) = QueryBuilder(managerAccountName, System.currentTimeMillis(), 1003L)
        .getSignatories(accountId)
        .buildUnsigned()

    /**
     * Creates a simple `RemoveSignatory` transaction
     * @return unsigned `RemoveSignatory` transaction
     */
    private fun createRemoveSignatoryTransaction(publicKey: ByteArray) = TransactionBuilder(userAtDomain, System.currentTimeMillis())
        .removeSignatory(userAtDomain, publicKey)
        .build()

    /**
     * Creates a simple `SetAccountDetail` transaction
     * @return unsigned `SetAccountDetail` transaction
     */
    private fun createTransaction() = TransactionBuilder(userAtDomain, System.currentTimeMillis())
        .setAccountDetail(userAtDomain, "time", System.currentTimeMillis().toString())
        .build()

    /**
     * Creates a signature object
     * @param signatureBytes - signature bytes
     * @return signature
     */
    private fun formSignature(signatureBytes: ByteArray, publicKey: ByteArray?): Primitive.Signature {
        return Primitive.Signature.newBuilder()
            .setSignature(Utils.toHex(signatureBytes))
            .setPublicKey(Utils.toHex(publicKey))
            .build()
    }
}
