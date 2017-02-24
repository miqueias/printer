package com.marlon.myapplication;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.datecs.api.BuildInfo;
import com.datecs.api.card.FinancialCard;
import com.datecs.api.emsr.EMSR;
import com.datecs.api.emsr.EMSR.EMSRInformation;
import com.datecs.api.emsr.EMSR.EMSRKeyInformation;
import com.datecs.api.printer.Printer;
import com.datecs.api.printer.Printer.ConnectionListener;
import com.datecs.api.printer.PrinterInformation;
import com.datecs.api.printer.ProtocolAdapter;
import com.datecs.api.rfid.ContactlessCard;
import com.datecs.api.rfid.FeliCaCard;
import com.datecs.api.rfid.ISO14443Card;
import com.datecs.api.rfid.ISO15693Card;
import com.datecs.api.rfid.RC663;
import com.datecs.api.rfid.RC663.CardListener;
import com.datecs.api.rfid.STSRICard;
import com.datecs.api.universalreader.UniversalReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import network.PrinterServer;
import network.PrinterServerListener;
import pojo.Betting;
import pojo.Game;
import pojo.Ticket;
import pojo.Validator;
import util.CryptographyHelper;
import util.DeviceListActivity;
import util.HexUtil;

public class PrinterActivity extends AppCompatActivity {

    // URL to get contacts JSON
    //private static String url = "http://api.androidhive.info/contacts/";
    private static String url = "http://104.236.126.197/bilhete/12/imprimir/android";


    ArrayList<HashMap<String, String>> contactList;


    private String TAG = MainActivity.class.getSimpleName();

    private ProgressDialog pDialog;
    private ListView lv;

    private static final String LOG_TAG = "PrinterSample";

    // Request to get the bluetooth device
    private static final int REQUEST_GET_DEVICE = 0;

    // Request to get the bluetooth device
    private static final int DEFAULT_NETWORK_PORT = 9100;

    // Interface, used to invoke asynchronous printer operation.
    private interface PrinterRunnable {
        public void run(ProgressDialog dialog, Printer printer) throws IOException;
    }

    // Member variables
    private ProtocolAdapter mProtocolAdapter;
    private ProtocolAdapter.Channel mPrinterChannel;
    private ProtocolAdapter.Channel mUniversalChannel;
    private Printer mPrinter;
    private EMSR mEMSR;
    private PrinterServer mPrinterServer;
    private BluetoothSocket mBtSocket;
    private Socket mNetSocket;
    private RC663 mRC663;
    private EditText etTexto;
    private String myTexto;
    private Ticket ticket;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer);

        contactList = new ArrayList<>();

        lv = (ListView) findViewById(R.id.list);
        etTexto = (EditText) findViewById(R.id.etTexto);

        Uri data = getIntent().getData();

        getParamsUrl();

        new GetContacts().execute();

        // Show Android device information and API version.
        final TextView txtVersion = (TextView) findViewById(R.id.txt_version);
        txtVersion.setText(Build.MANUFACTURER + " " + Build.MODEL + ", Datecs API "
                + BuildInfo.VERSION);

        findViewById(R.id.btn_print_self_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printSelfTest();
            }
        });

        findViewById(R.id.btn_print_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printText();
            }
        });

        findViewById(R.id.btn_print_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printImage();
            }
        });

        findViewById(R.id.btn_print_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printPage();
            }
        });

        waitForConnection();
    }

    @Override
    public void onResume() {
        super.onResume();
        getParamsUrl();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        getParamsUrl();
    }

    private void getParamsUrl() {
        Uri data = getIntent().getData();

        if (data != null) {
            String scheme = data.getScheme(); // "http"
            String host = data.getHost(); // "twitter.com"
            List<String> params = data.getPathSegments();
            String first = params.get(0); // "status"
            String second = params.get(1); // "1234"

            if (!second.equals("")) {
                url = "http://104.236.126.197/bilhete/"+second+"/imprimir/android";
                etTexto.setText(url);
            }
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeActiveConnection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GET_DEVICE) {
            if (resultCode == DeviceListActivity.RESULT_OK) {
                String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // address = "192.168.11.136:9100";
                if (BluetoothAdapter.checkBluetoothAddress(address)) {
                    establishBluetoothConnection(address);
                } else {
                    establishNetworkConnection(address);
                }
            } else {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (findViewById(R.id.content_buttons).getVisibility() == View.INVISIBLE) {
            findViewById(R.id.content_buttons).setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    private void toast(final String text) {
        Log.d(LOG_TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void error(final String text) {
        Log.w(LOG_TAG, text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void dialog(final int iconResId, final String title, final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(PrinterActivity.this);
                builder.setIcon(iconResId);
                builder.setTitle(title);
                builder.setMessage(msg);
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });

                AlertDialog dlg = builder.create();
                dlg.show();
            }
        });
    }

    private void status(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (text != null) {
                    findViewById(R.id.panel_status).setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.txt_status)).setText(text);
                } else {
                    findViewById(R.id.panel_status).setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private void runTask(final PrinterRunnable r, final int msgResId) {
        final ProgressDialog dialog = new ProgressDialog(PrinterActivity.this);
        dialog.setTitle(getString(R.string.title_please_wait));
        dialog.setMessage(getString(msgResId));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    r.run(dialog, mPrinter);
                } catch (IOException e) {
                    e.printStackTrace();
                    error("I/O error occurs: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    error("Critical error occurs: " + e.getMessage());
                    finish();
                } finally {
                    dialog.dismiss();
                }
            }
        });
        t.start();
    }

    protected void initPrinter(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        Log.d(LOG_TAG, "Initialize printer...");

        // Here you can enable various debug information
        //ProtocolAdapter.setDebug(true);
        Printer.setDebug(true);
        EMSR.setDebug(true);

        // Check if printer is into protocol mode. Ones the object is created it can not be released
        // without closing base streams.
        mProtocolAdapter = new ProtocolAdapter(inputStream, outputStream);
        if (mProtocolAdapter.isProtocolEnabled()) {
            Log.d(LOG_TAG, "Protocol mode is enabled");

            // Into protocol mode we can callbacks to receive printer notifications
            mProtocolAdapter.setPrinterListener(new ProtocolAdapter.PrinterListener() {
                @Override
                public void onThermalHeadStateChanged(boolean overheated) {
                    if (overheated) {
                        Log.d(LOG_TAG, "Thermal head is overheated");
                        status("OVERHEATED");
                    } else {
                        status(null);
                    }
                }

                @Override
                public void onPaperStateChanged(boolean hasPaper) {
                    if (hasPaper) {
                        Log.d(LOG_TAG, "Event: Paper out");
                        status("PAPER OUT");
                    } else {
                        status(null);
                    }
                }

                @Override
                public void onBatteryStateChanged(boolean lowBattery) {
                    if (lowBattery) {
                        Log.d(LOG_TAG, "Low battery");
                        status("LOW BATTERY");
                    } else {
                        status(null);
                    }
                }
            });


            // Get printer instance
            mPrinterChannel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_PRINTER);
            mPrinter = new Printer(mPrinterChannel.getInputStream(), mPrinterChannel.getOutputStream());

            // Check if printer has encrypted magnetic head
            ProtocolAdapter.Channel emsrChannel = mProtocolAdapter
                    .getChannel(ProtocolAdapter.CHANNEL_EMSR);
            try {
                // Close channel silently if it is already opened.
                try {
                    emsrChannel.close();
                } catch (IOException e) {
                }

                // Try to open EMSR channel. If method failed, then probably EMSR is not supported
                // on this device.
                emsrChannel.open();

                mEMSR = new EMSR(emsrChannel.getInputStream(), emsrChannel.getOutputStream());
                EMSRKeyInformation keyInfo = mEMSR.getKeyInformation(EMSR.KEY_AES_DATA_ENCRYPTION);
                if (!keyInfo.tampered && keyInfo.version == 0) {
                    Log.d(LOG_TAG, "Missing encryption key");
                    // If key version is zero we can load a new key in plain mode.
                    byte[] keyData = CryptographyHelper.createKeyExchangeBlock(0xFF,
                            EMSR.KEY_AES_DATA_ENCRYPTION, 1, CryptographyHelper.AES_DATA_KEY_BYTES,
                            null);
                    mEMSR.loadKey(keyData);
                }
                mEMSR.setEncryptionType(EMSR.ENCRYPTION_TYPE_AES256);
                mEMSR.enable();
                Log.d(LOG_TAG, "Encrypted magnetic stripe reader is available");
            } catch (IOException e) {
                if (mEMSR != null) {
                    mEMSR.close();
                    mEMSR = null;
                }
            }

            // Check if printer has encrypted magnetic head
            ProtocolAdapter.Channel rfidChannel = mProtocolAdapter
                    .getChannel(ProtocolAdapter.CHANNEL_RFID);

            try {
                // Close channel silently if it is already opened.
                try {
                    rfidChannel.close();
                } catch (IOException e) {
                }

                // Try to open RFID channel. If method failed, then probably RFID is not supported
                // on this device.
                rfidChannel.open();

                mRC663 = new RC663(rfidChannel.getInputStream(), rfidChannel.getOutputStream());
                mRC663.setCardListener(new CardListener() {
                    @Override
                    public void onCardDetect(ContactlessCard card) {
                        processContactlessCard(card);
                    }
                });
                mRC663.enable();
                Log.d(LOG_TAG, "RC663 reader is available");
            } catch (IOException e) {
                if (mRC663 != null) {
                    mRC663.close();
                    mRC663 = null;
                }
            }

            // Check if printer has encrypted magnetic head
            mUniversalChannel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_UNIVERSAL_READER);
            new UniversalReader(mUniversalChannel.getInputStream(), mUniversalChannel.getOutputStream());

        } else {
            Log.d(LOG_TAG, "Protocol mode is disabled");

            // Protocol mode it not enables, so we should use the row streams.
            mPrinter = new Printer(mProtocolAdapter.getRawInputStream(),
                    mProtocolAdapter.getRawOutputStream());
        }

        mPrinter.setConnectionListener(new ConnectionListener() {
            @Override
            public void onDisconnect() {
                toast("Printer is disconnected");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            waitForConnection();
                        }
                    }
                });
            }
        });

    }

    private synchronized void waitForConnection() {
        status(null);

        closeActiveConnection();

        // Show dialog to select a Bluetooth device.
        startActivityForResult(new Intent(this, DeviceListActivity.class), REQUEST_GET_DEVICE);

        // Start server to listen for network connection.
        try {
            mPrinterServer = new PrinterServer(new PrinterServerListener() {
                @Override
                public void onConnect(Socket socket) {
                    Log.d(LOG_TAG, "Accept connection from "
                            + socket.getRemoteSocketAddress().toString());

                    // Close Bluetooth selection dialog
                    finishActivity(REQUEST_GET_DEVICE);

                    mNetSocket = socket;
                    try {
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        initPrinter(in, out);
                    } catch (IOException e) {
                        e.printStackTrace();
                        error("FAILED to initialize: " + e.getMessage());
                        waitForConnection();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void establishBluetoothConnection(final String address) {
        final ProgressDialog dialog = new ProgressDialog(PrinterActivity.this);
        dialog.setTitle(getString(R.string.title_please_wait));
        dialog.setMessage(getString(R.string.msg_connecting));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        closePrinterServer();

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "Connecting to " + address + "...");

                btAdapter.cancelDiscovery();

                try {
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                    BluetoothDevice btDevice = btAdapter.getRemoteDevice(address);

                    InputStream in = null;
                    OutputStream out = null;

                    try {
                        BluetoothSocket btSocket = btDevice.createRfcommSocketToServiceRecord(uuid);
                        btSocket.connect();

                        mBtSocket = btSocket;
                        in = mBtSocket.getInputStream();
                        out = mBtSocket.getOutputStream();
                    } catch (IOException e) {
                        error("FAILED to connect: " + e.getMessage());
                        waitForConnection();
                        return;
                    }

                    try {
                        initPrinter(in, out);
                    } catch (IOException e) {
                        error("FAILED to initiallize: " + e.getMessage());
                        return;
                    }
                } finally {
                    dialog.dismiss();
                }
            }
        });
        t.start();
    }

    private void establishNetworkConnection(final String address) {
        closePrinterServer();

        final ProgressDialog dialog = new ProgressDialog(PrinterActivity.this);
        dialog.setTitle(getString(R.string.title_please_wait));
        dialog.setMessage(getString(R.string.msg_connecting));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        closePrinterServer();

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "Connectiong to " + address + "...");
                try {
                    Socket s = null;
                    try {
                        String[] url = address.split(":");
                        int port = DEFAULT_NETWORK_PORT;

                        try {
                            if (url.length > 1) {
                                port = Integer.parseInt(url[1]);
                            }
                        } catch (NumberFormatException e) {
                        }

                        s = new Socket(url[0], port);
                        s.setKeepAlive(true);
                        s.setTcpNoDelay(true);
                    } catch (UnknownHostException e) {
                        error("FAILED to connect: " + e.getMessage());
                        waitForConnection();
                        return;
                    } catch (IOException e) {
                        error("FAILED to connect: " + e.getMessage());
                        waitForConnection();
                        return;
                    }

                    InputStream in = null;
                    OutputStream out = null;

                    try {
                        mNetSocket = s;
                        in = mNetSocket.getInputStream();
                        out = mNetSocket.getOutputStream();
                    } catch (IOException e) {
                        error("FAILED to connect: " + e.getMessage());
                        waitForConnection();
                        return;
                    }

                    try {
                        initPrinter(in, out);
                    } catch (IOException e) {
                        error("FAILED to initiallize: " + e.getMessage());
                        return;
                    }
                } finally {
                    dialog.dismiss();
                }
            }
        });
        t.start();
    }

    private synchronized void closeBluetoothConnection() {
        // Close Bluetooth connection
        BluetoothSocket s = mBtSocket;
        mBtSocket = null;
        if (s != null) {
            Log.d(LOG_TAG, "Close Bluetooth socket");
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void closeNetworkConnection() {
        // Close network connection
        Socket s = mNetSocket;
        mNetSocket = null;
        if (s != null) {
            Log.d(LOG_TAG, "Close Network socket");
            try {
                s.shutdownInput();
                s.shutdownOutput();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void closePrinterServer() {
        closeNetworkConnection();

        // Close network server
        PrinterServer ps = mPrinterServer;
        mPrinterServer = null;
        if (ps != null) {
            Log.d(LOG_TAG, "Close Network server");
            try {
                ps.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void closePrinterConnection() {
        if (mRC663 != null) {
            try {
                mRC663.disable();
            } catch (IOException e) {
            }

            mRC663.close();
        }

        if (mEMSR != null) {
            mEMSR.close();
        }

        if (mPrinter != null) {
            mPrinter.close();
        }

        if (mProtocolAdapter != null) {
            mProtocolAdapter.close();
        }
    }

    private synchronized void closeActiveConnection() {
        closePrinterConnection();
        closeBluetoothConnection();
        closeNetworkConnection();
        closePrinterServer();
    }


    private void printSelfTest() {
        Log.d(LOG_TAG, "Print Self Test");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                printer.printSelfTest();
                printer.flush();
            }
        }, R.string.msg_printing_self_test);
    }

    private void printText() {
        Log.d(LOG_TAG, "Print Text");
        myTexto = etTexto.getText().toString();

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                StringBuffer textBuffer = new StringBuffer();

                if (ticket != null) {

                    int contJogos = 0;
                    double valorFinal = 0;

                    myTexto = "";
                    myTexto = myTexto + "MeioNorte Sports" + "\n";
                    myTexto = myTexto + "Identificacao:" + ticket.id + "\n";
                    myTexto = myTexto + "Apostador: " + ticket.gambler_name + "\n";
                    myTexto = myTexto + "Cambista: " + ticket.validator.first_name + "\n";
                    myTexto = myTexto + "Valor Apostado: " + ticket.ticket_value + "\n";
                    myTexto = myTexto + "Horario: " + ticket.updated_at + "\n";
                    myTexto = myTexto + "- - - - - - - - - - - - - - - - - - - -" + "\n";

                    valorFinal = Double.parseDouble(ticket.ticket_value);

                    if (ticket.betting.size() > 0) {
                        for (int i = 0; i < ticket.betting.size(); i++) {

                            Betting betting = ticket.betting.get(i);

                            String metrincName = "";
                            Double modificador = 0.0;

                            switch (betting.getMetric_name()) {
                                case "xc":
                                    metrincName = "XC";
                                    modificador = betting.getGame().getXc();

                                    break;
                                case "xf":
                                    metrincName = "XF";
                                    modificador = betting.getGame().getXf();

                                    break;
                                case "gmc":
                                    metrincName = "GMC";
                                    modificador = betting.getGame().getGmc();

                                    break;
                                case "gmf":
                                    metrincName = "GMF";
                                    modificador = betting.getGame().getGmf();

                                    break;
                                case "amb":
                                    metrincName = "AMB";
                                    modificador = betting.getGame().getAmb();

                                    break;
                                case "na":
                                    metrincName = "NA";
                                    modificador = betting.getGame().getNa();

                                    break;
                                case "two_gm_plus":
                                    metrincName = "+2GM";
                                    modificador = betting.getGame().getTwo_gm_plus();

                                    break;
                                case "two_gm_low":
                                    metrincName = "-2GM";
                                    modificador = betting.getGame().getTwo_gm_low();

                                    break;
                                case "cvfm":
                                    metrincName = "CVFM";
                                    modificador = betting.getGame().getCvfm();

                                    break;
                                case "tcvg":
                                    metrincName = "TCVG";
                                    modificador = betting.getGame().getTcvg();

                                    break;
                                case "eg":
                                    metrincName = "EG";
                                    modificador = betting.getGame().getEg();

                                    break;
                                case "egg":
                                    metrincName = "EGG";
                                    modificador = betting.getGame().getEgg();

                                    break;
                                case "house":
                                    metrincName = "CASA";
                                    modificador = betting.getGame().getHouse();

                                    break;
                                case "tie":
                                    metrincName = "EMPATE";
                                    modificador = betting.getGame().getTie();

                                    break;
                                case "visitor":
                                    metrincName = "VISITANTE";
                                    modificador = betting.getGame().getVisitor();

                                    break;
                            }


                            myTexto = myTexto + betting.getGame().getGame_date() + " " + betting.getGame().getGame_time() + " " +
                                    betting.getGame().getHome_team().toUpperCase() + " x " + betting.getGame().getOut_team().toUpperCase() + "\n";
                            //myTexto = myTexto + "Data do Jogo: " + betting.getGame().getGame_date() + "\n";
                            //myTexto = myTexto + "Hora do Jogo: " + betting.getGame().getGame_time() + "\n";
                            myTexto = myTexto + metrincName +": "+ modificador + "\n";

                            //betting = null;
                            contJogos++;
                            valorFinal *= modificador;//  valorFinal + (Double.parseDouble(ticket.ticket_value) * modificador);
                        }

                        myTexto = myTexto + "- - - - - - - - - - - - - - - - - - - -" + "\n";
                        myTexto = myTexto + "Qtd. de Jogos: " + contJogos + "\n";
                        myTexto = myTexto + "Valor Apostado: " + ticket.ticket_value + "\n";;

                        int decimalPlaces = 2;
                        BigDecimal bd = new BigDecimal(valorFinal);
                        bd = bd.setScale(decimalPlaces, BigDecimal.ROUND_HALF_UP);
                        valorFinal = bd.doubleValue();

                        myTexto = myTexto + "Retorno Possivel: " + valorFinal + "\n";
                        myTexto = myTexto + "- - - - - - - - - - - - - - - - - - - -" + "\n";
                        myTexto = myTexto + "Limite de pemios: R$ 20.000,00 e temos ate 48hs para realizar o pagamento.";

                    }
                } else {
                    myTexto = "NÃO HÁ DADOS PARA IMPRIMIR";
                }

                textBuffer.append(myTexto);

                printer.reset();
                printer.printTaggedText(textBuffer.toString());
                printer.setAlign(Printer.ALIGN_CENTER);
                printer.feedPaper(110);
                printer.flush();


            }
        }, R.string.msg_printing_text);
    }

    private void printImage() {
        Log.d(LOG_TAG, "Print Image");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;

                final AssetManager assetManager = getApplicationContext().getAssets();
                final Bitmap bitmap = BitmapFactory.decodeStream(assetManager.open("sample.png"),
                        null, options);
                final int width = bitmap.getWidth();
                final int height = bitmap.getHeight();
                final int[] argb = new int[width * height];
                bitmap.getPixels(argb, 0, width, 0, 0, width, height);
                bitmap.recycle();

                printer.reset();
                printer.printCompressedImage(argb, width, height, Printer.ALIGN_CENTER, true);
                printer.feedPaper(110);
                printer.flush();
            }
        }, R.string.msg_printing_image);
    }

    private void printPage() {
        Log.d(LOG_TAG, "Print Page");

        runTask(new PrinterRunnable() {
            @Override
            public void run(ProgressDialog dialog, Printer printer) throws IOException {
                PrinterInformation pi = printer.getInformation();

                if (!pi.isPageSupported()) {
                    dialog(R.drawable.ic_page, getString(R.string.title_warning),
                            getString(R.string.msg_unsupport_page_mode));
                    return;
                }

                printer.reset();
                printer.selectPageMode();

                printer.setPageRegion(0, 0, 160, 320, Printer.PAGE_LEFT);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH I{br}");
                printer.drawPageRectangle(0, 0, 160, 32, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from left to right"
                        + ", feed to bottom. Starting point in left top corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(160, 0, 160, 320, Printer.PAGE_TOP);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH II{br}");
                printer.drawPageRectangle(160 - 32, 0, 32, 320, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from top to bottom"
                        + ", feed to left. Starting point in right top corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(160, 320, 160, 320, Printer.PAGE_RIGHT);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH III{br}");
                printer.drawPageRectangle(0, 320 - 32, 160, 32, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from right to left"
                        + ", feed to top. Starting point in right bottom corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.setPageRegion(0, 320, 160, 320, Printer.PAGE_BOTTOM);
                printer.setPageXY(0, 4);
                printer.printTaggedText("{reset}{center}{b}PARAGRAPH IV{br}");
                printer.drawPageRectangle(0, 0, 32, 320, Printer.FILL_INVERTED);
                printer.setPageXY(0, 34);
                printer.printTaggedText("{reset}Text printed from bottom to top"
                        + ", feed to right. Starting point in left bottom corner of the page.{br}");
                printer.drawPageFrame(0, 0, 160, 320, Printer.FILL_BLACK, 1);

                printer.printPage();
                printer.selectStandardMode();
                printer.feedPaper(110);
                printer.flush();
            }
        }, R.string.msg_printing_page);
    }


    private void processContactlessCard(ContactlessCard contactlessCard) {
        final StringBuffer msgBuf = new StringBuffer();

        if (contactlessCard instanceof ISO14443Card) {
            ISO14443Card card = (ISO14443Card) contactlessCard;
            msgBuf.append("ISO14 card: " + HexUtil.byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("ISO14 type: " + card.type + "\n");

            if (card.type == ContactlessCard.CARD_MIFARE_DESFIRE) {
                ProtocolAdapter.setDebug(true);
                mPrinterChannel.suspend();
                mUniversalChannel.suspend();
                try {

                    card.getATS();
                    Log.d(LOG_TAG, "Select application");
                    card.DESFire().selectApplication(0x78E127);
                    Log.d(LOG_TAG, "Application is selected");
                    msgBuf.append("DESFire Application: " + Integer.toHexString(0x78E127) + "\n");
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Select application", e);
                } finally {
                    ProtocolAdapter.setDebug(false);
                    mPrinterChannel.resume();
                    mUniversalChannel.resume();
                }
            }
            /*
             // 16 bytes reading and 16 bytes writing
             // Try to authenticate first with default key
            byte[] key= new byte[] {-1, -1, -1, -1, -1, -1};
            // It is best to store the keys you are going to use once in the device memory,
            // then use AuthByLoadedKey function to authenticate blocks rather than having the key in your program
            card.authenticate('A', 8, key);

            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F };
            card.write16(8, input);

            // Read data from card
            byte[] result = card.read16(8);
            */
        } else if (contactlessCard instanceof ISO15693Card) {
            ISO15693Card card = (ISO15693Card) contactlessCard;

            msgBuf.append("ISO15 card: " + HexUtil.byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("Block size: " + card.blockSize + "\n");
            msgBuf.append("Max blocks: " + card.maxBlocks + "\n");

            /*
            if (card.blockSize > 0) {
                byte[] security = card.getBlocksSecurityStatus(0, 16);
                ...

                // Write data to the card
                byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03 };
                card.write(0, input);
                ...

                // Read data from card
                byte[] result = card.read(0, 1);
                ...
            }
            */
        } else if (contactlessCard instanceof FeliCaCard) {
            FeliCaCard card = (FeliCaCard) contactlessCard;

            msgBuf.append("FeliCa card: " + HexUtil.byteArrayToHexString(card.uid) + "\n");

            /*
            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F };
            card.write(0x0900, 0, input);
            ...

            // Read data from card
            byte[] result = card.read(0x0900, 0, 1);
            ...
            */
        } else if (contactlessCard instanceof STSRICard) {
            STSRICard card = (STSRICard) contactlessCard;

            msgBuf.append("STSRI card: " + HexUtil.byteArrayToHexString(card.uid) + "\n");
            msgBuf.append("Block size: " + card.blockSize + "\n");

            /*
            // Write data to the card
            byte[] input = new byte[] { 0x00, 0x01, 0x02, 0x03 };
            card.writeBlock(8, input);
            ...

            // Try reading two blocks
            byte[] result = card.readBlock(8);
            ...
            */
        } else {
            msgBuf.append("Contactless card: " + HexUtil.byteArrayToHexString(contactlessCard.uid));
        }

        dialog(R.drawable.ic_tag, getString(R.string.tag_info), msgBuf.toString());

        // Wait silently to remove card
        try {
            contactlessCard.waitRemove();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Async task class to get json by making HTTP call
     */
    private class GetContacts extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Showing progress dialog
            pDialog = new ProgressDialog(PrinterActivity.this);
            pDialog.setMessage("Please wait...");
            pDialog.setCancelable(false);
            pDialog.show();

        }

        @Override
        protected Void doInBackground(Void... arg0) {
            HttpHandler sh = new HttpHandler();

            // Making a request to url and getting response
            //String jsonStr = sh.makeServiceCall(myTexto);
            String jsonStr = sh.makeServiceCall(url);

            Log.e(TAG, "Response from url: " + jsonStr);

            if (jsonStr != null) {
                try {
                    JSONObject jsonObj = new JSONObject(jsonStr);

                    ticket = new Ticket();

                    ticket.id = Integer.parseInt(jsonObj.get("id").toString());
                    ticket.gambler_name = jsonObj.get("gambler_name").toString();
                    ticket.ticket_value = jsonObj.get("ticket_value").toString();
                    ticket.validated_user = Integer.parseInt(jsonObj.get("validated_user").toString());
                    ticket.validated_date = jsonObj.get("validated_date").toString();
                    ticket.validated_commission = jsonObj.get("validated_commission").toString();
                    ticket.created_at = jsonObj.get("created_at").toString();
                    ticket.updated_at = jsonObj.get("updated_at").toString();
                    ticket.deleted_at = jsonObj.get("deleted_at").toString();

                    JSONObject jsonObjectValidator = jsonObj.getJSONObject("validator");

                    Validator validator = new Validator();

                    validator.id = Integer.parseInt(jsonObjectValidator.get("id").toString());
                    validator.email = jsonObjectValidator.get("email").toString();
                    validator.cpf = jsonObjectValidator.get("cpf").toString();
                    validator.pin = jsonObjectValidator.get("pin").toString();
                    validator.role_id = Integer.parseInt(jsonObjectValidator.get("role_id").toString());
                    validator.first_name = jsonObjectValidator.get("first_name").toString();
                    validator.last_name = jsonObjectValidator.get("last_name").toString();
                    validator.phone = jsonObjectValidator.get("phone").toString();
                    validator.city = jsonObjectValidator.get("city").toString();
                    validator.neighborhood = jsonObjectValidator.get("neighborhood").toString();
                    validator.street = jsonObjectValidator.get("street").toString();
                    validator.street_number = jsonObjectValidator.get("street_number").toString();
                    validator.commission = jsonObjectValidator.get("commission").toString();
                    validator.limit_amount = jsonObjectValidator.get("limit_amount").toString();
                    validator.limit_money_day = jsonObjectValidator.get("limit_money_day").toString();
                    validator.active = jsonObjectValidator.get("active").toString();
                    validator.created_at = jsonObjectValidator.get("created_at").toString();
                    validator.updated_at = jsonObjectValidator.get("updated_at").toString();
                    validator.deleted_at = jsonObjectValidator.get("deleted_at").toString();

                    ticket.validator = validator;

                    JSONArray jsonArrayBetting = jsonObj.getJSONArray("betting");

                    ArrayList<Betting> bettings = new ArrayList<>();
                    for (int i = 0; i < jsonArrayBetting.length(); i++) {
                        JSONObject jsonObjectBetting = jsonArrayBetting.getJSONObject(i);

                        Betting betting = new Betting();
                        betting.setId(Integer.parseInt(jsonObjectBetting.get("id").toString()));
                        betting.setMetric_name(jsonObjectBetting.get("metric_name").toString());
                        betting.setWinner(jsonObjectBetting.get("winner").toString());
                        betting.setGame_id(Integer.parseInt(jsonObjectBetting.get("game_id").toString()));
                        betting.setTicket_id(Integer.parseInt(jsonObjectBetting.get("ticket_id").toString()));
                        betting.setCreated_at(jsonObjectBetting.get("created_at").toString());
                        betting.setUpdated_at(jsonObjectBetting.get("updated_at").toString());
                        betting.setDeleted_at(jsonObjectBetting.get("deleted_at").toString());

                        JSONObject jsonObjectGame = jsonObjectBetting.getJSONObject("game");

                        Game game = new Game();
                        game.setId(Integer.parseInt(jsonObjectGame.get("id").toString()));
                        game.setUser_id(Integer.parseInt(jsonObjectGame.get("user_id").toString()));
                        game.setGame_date(jsonObjectGame.get("game_date").toString());
                        game.setGame_time(jsonObjectGame.get("game_time").toString());
                        game.setHome_team(jsonObjectGame.get("home_team").toString());
                        game.setOut_team(jsonObjectGame.get("out_team").toString());
                        game.setChampionship(jsonObjectGame.get("championship").toString());
                        game.setHome_goals(jsonObjectGame.get("home_goals").toString());
                        game.setOut_goals(jsonObjectGame.get("out_goals").toString());
                        game.setHouse(Double.parseDouble(jsonObjectGame.get("house").toString()));
                        game.setXc(Double.parseDouble(jsonObjectGame.get("xc").toString()));
                        game.setXf(Double.parseDouble(jsonObjectGame.get("xf").toString()));
                        game.setGmc(Double.parseDouble(jsonObjectGame.get("gmc").toString()));
                        game.setGmf(Double.parseDouble(jsonObjectGame.get("gmf").toString()));
                        game.setAmb(Double.parseDouble(jsonObjectGame.get("amb").toString()));
                        game.setNa(Double.parseDouble(jsonObjectGame.get("na").toString()));
                        game.setTwo_gm_plus(Double.parseDouble(jsonObjectGame.get("two_gm_plus").toString()));
                        game.setTwo_gm_low(Double.parseDouble(jsonObjectGame.get("two_gm_low").toString()));
                        game.setCvfm(Double.parseDouble(jsonObjectGame.get("cvfm").toString()));
                        game.setTcvg(Double.parseDouble(jsonObjectGame.get("tcvg").toString()));
                        game.setEg(Double.parseDouble(jsonObjectGame.get("eg").toString()));
                        game.setEgg(Double.parseDouble(jsonObjectGame.get("egg").toString()));
                        game.setTie(Double.parseDouble(jsonObjectGame.get("tie").toString()));
                        game.setVisitor(Double.parseDouble(jsonObjectGame.get("visitor").toString()));

                        betting.setGame(game);
                        bettings.add(betting);
                    }

                    ticket.betting = bettings;
                } catch (final JSONException e) {
                    Log.e(TAG, "Json parsing error: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    "Json parsing error: " + e.getMessage(),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

                }
            } else {
                Log.e(TAG, "Couldn't get json from server.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),
                                "Couldn't get json from server. Check LogCat for possible errors!",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });

            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();
            /**
             * Updating parsed JSON data into ListView
             * */
            ListAdapter adapter = new SimpleAdapter(
                    PrinterActivity.this, contactList,
                    R.layout.list_item, new String[]{"name", "email",
                    "mobile"}, new int[]{R.id.name,
                    R.id.email, R.id.mobile});

            //lv.setAdapter(adapter);
        }
    }
}
