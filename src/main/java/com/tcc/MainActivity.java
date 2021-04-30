package com.tcc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


   /*
    01 04 - ENGINE_LOAD
    01 05 - ENGINE_COOLANT_TEMPERATURE
    01 0C - ENGINE_RPM
    01 0D - VEHICLE_SPEED
    01 0F - INTAKE_AIR_TEMPERATURE
    01 10 - MASS_AIR_FLOW
    01 11 - THROTTLE_POSITION_PERCENTAGE
    01 1F - ENGINE_RUN_TIME
    01 2F - FUEL_LEVEL
    01 46 - AMBIENT_AIR_TEMPERATURE
    01 51 - FUEL_TYPE
    01 5E - FUEL_CONSUMPTION_1
    01 5F - FUEL_CONSUMPTION_2
   */

    // Troca de informações dentro do HANDLER
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 3;
    private static final int REQUEST_ENABLE_BT = 4;

    // Lista de comandos
    final List<String> listaComandos = new ArrayList<>();

    //Gerencia o Menu
    MenuItem itemMenu;

    //Representa um dispositivo Bluetooth remoto. A BluetoothDevice permite criar uma conexão com o respectivo dispositivo ou consultar
    // informações sobre ele, como nome, endereço, classe e estado de ligação.
    BluetoothDevice currentdevice;

    //Ajuda a saber se o usuário está tentando se conectar ou se já foi conectado
    boolean inicializado = false, tentaConectar = false;

    // Nome do hardware(ELM327) e o protocolo em uso
    String nomeOBD2 = null, protocoloUsado = null;

    //Envia Pids padrões para o hardware inicializar de forma correta
    String[] inicializaComando;

    // Intent para fazer a ponto nos dados trocados
    Intent serverIntent = null;

    // ALGUNS PIDS PARA INSERIR NA LISTA
    String VOLTAGE = "ATRV", // BATERIA
            RESET = "ATZ", // RESETA
            TEMP_AGUA = "0105",  //A-40
            RPM = "010C",  //((A*256)+B)/4
            CARGA_MOTOR = "0104",  // A*100/255
            TEMP_AR = "010F";  //A-40

    //Para inserir um novo PID ou remover basta ir neste array
    String[] pidsCadastrados;

    // TELA
    Toolbar toolbar;
    AppBarLayout appbar;
    private Menu menu;


    private TextView cargaMotor, voltage, tempAgua, Status, tempAr, rotacaoMotor, textoTempAgua, rotacaoTexto;

    private int valRpm = 0, valTemAr = 0, valTemAgua = 0, posComando = 0, valVelocidade;

    //Representa o adaptador Bluetooth do dispositivo local. O BluetoothAdapter permite executar tarefas fundamentais Bluetooth,
    // como iniciar a descoberta de dispositivos, consulta uma lista de dispositivos ligados (emparelhados),
    // instanciar um BluetoothDevice usando um endereço MAC conhecido,
    // e criar uma BluetoothServerSocketpara escutar as solicitações de conexão de outros dispositivos
    private BluetoothAdapter mBluetoothAdapter = null;


    // Troca de dados usando a Classe "BluetoothService" - Onde há threads que faz a conexão de forma correta entre os dispositivos
    //  e escreve os Pids passados por esta Activity e envia para o handler os bytes de retorno para realização da leitura.
    private BluetoothService mBtService = null;


    // Um manipulador permite enviar e processar Message objetos Runnable associados a um encadeamento MessageQueue
    private final Handler mBtHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:

                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:

                            try {
                                itemMenu = menu.findItem(R.id.menu_conectar);

                            } catch (Exception e) {
                                Toast.makeText(getApplicationContext(), "ERRO: " + e, Toast.LENGTH_LONG).show();
                            }

                            tentaConectar = false;
                            enviaMsgECU(RESET);

                            break;
                        case BluetoothService.STATE_CONNECTING:
                            Status.setText(R.string.conectando);

                            break;
                    }
                    break;

                case MESSAGE_READ:
                    analisaMsg(msg);

                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        appbar = findViewById(R.id.appbar);

        // PARA A TELA FICAR FULLSCREEN
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Status = findViewById(R.id.Status);
        cargaMotor = findViewById(R.id.cargaMotor);
        tempAgua = findViewById(R.id.tempAgua);
        voltage = findViewById(R.id.Volt);
        tempAr = findViewById(R.id.tempAr);
        rotacaoMotor = findViewById(R.id.Rotacao);
        textoTempAgua = findViewById(R.id.tempAgua_Texto);
        rotacaoTexto = findViewById(R.id.rotacao_texto);

        //ATZ reset all
        //ATDP Describe the current Protocol
        //ATAT0-1-2 Adaptive Timing Off - daptive Timing Auto1 - daptive Timing Auto2
        //ATE0-1 Echo Off - Echo On
        //ATSP0 Set Protocol to Auto and save it
        //ATMA Monitor All
        //ATL1-0 Linefeeds On - Linefeeds Off
        //ATH1-0 Headers On - Headers Off
        //ATS1-0 printing of Spaces On - printing of Spaces Off
        //ATAL Allow Long (>7 byte) messages
        //ATRD Read the stored data
        //ATSTFF Set time out to maximum
        //ATSTHH Set timeout to 4ms

        // COMANDOS DE INICIALIZAÇÃO DO HARDWARE OBD2 ELM327
        inicializaComando = new String[]{"ATZ", "ATL0", "ATE1", "ATH1", "ATAT1", "ATSTFF", "ATI", "ATDP", "ATSP0", "ATSP0"};

        // PIDS INSERIDOS NO ARRAY -- NÃO INSERIR O RESET
        pidsCadastrados = new String[]{
                VOLTAGE,
                TEMP_AGUA,
                RPM,
                CARGA_MOTOR,
                TEMP_AR
        };

        // CHECA SE O DISPOSITIVO TEM BLUETOOTH
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth não disponível neste aparelho.", Toast.LENGTH_LONG).show();
        } else {
            if (mBtService != null) {
                // SE o serviço estiver parado, então inicia uma conexão entre eles.
                if (mBtService.getState() == BluetoothService.STATE_NONE) {
                    mBtService.start();
                }
            }
        }


        //Inseri os pids cadastrados na lista de comandos que será enviada à UCE
        inserirPID();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Adiciona itens à barra de ação, se estiver presente.
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.menu_conectar:

                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    return false;
                }

                if (mBtService == null) {
                    //Instancia a Classe BluetoothService e envia para o construtor o handler
                    setupBluetooth();
                }

                if (item.getTitle().equals("Conectar")) {
                    // Inicie o DeviceListActivity para ver os dispositivos pareados
                    serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    if (mBtService != null) {
                        mBtService.stop();
                        item.setTitle(R.string.conectar);
                    }
                }

                return true;


            case R.id.menu_exit:
                exit();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // Quando DeviceListActivity retorna com um dispositivo para conectar
                if (resultCode == MainActivity.RESULT_OK) {
                    connectDevice(data);
                }
                break;

            case REQUEST_ENABLE_BT:

                if (mBtService == null) {
                    setupBluetooth();
                }

                if (resultCode == MainActivity.RESULT_OK) {
                    serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    Toast.makeText(getApplicationContext(), "Bluetooth não está ativado!", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        inserirPID();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mBtService != null) {
            mBtService.stop();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        inserirPID();
    }

    @Override
    public void onStop() {

        super.onStop();
    }

    private void exit() {
        if (mBtService != null) {
            mBtService.stop();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    //INSERI PIDS EM UMA LISTA
    private void inserirPID() {

        int i = 0;
        for (String pid : pidsCadastrados) {
            listaComandos.add(i, pid);
            i++;
        }


    }


    // PEGA O MAC DO OBD2 ELM327
    private void connectDevice(Intent data) {

        tentaConectar = true;

        // Pega o MAC address do dispositivo
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        // Obtem o objeto BluetoothDevice
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        try {
            // Tentativa de se conectar ao dispositivo
            mBtService.connect(device);
            currentdevice = device;

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "ERRO: " + e, Toast.LENGTH_LONG).show();
        }
    }


    private void setupBluetooth() {
        // Inicialize o BluetoothService para executar conexões bluetooth
        mBtService = new BluetoothService(mBtHandler);

    }

    //ENVIA MENSAGEM À ECU ESCREVENDO OS BYTES ATRAVÉS DO MÉTODO WRITE()
    private void enviaMsgECU(String message) {

        try {
            if (message.length() > 0) {

                message = message + "\r";
                // Obtem os bytes da mensagem e envia para o método write() efetuar a gravação dos dados
                byte[] send = message.getBytes();
                mBtService.write(send);
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "ERRO: " + e, Toast.LENGTH_LONG).show();
        }

    }

    //Envia comandos de inicialização assim que o hardware estiver conectado ao APP
    private void enviaComandoInicializacao() {
        if (inicializaComando.length != 0) {

            if (posComando < 0) {
                posComando = 0;
            }

            String send = inicializaComando[posComando];
            enviaMsgECU(send);

            if (posComando == inicializaComando.length - 1) {
                inicializado = true;
                posComando = 0;
                enviaComandosECU();
            } else {
                posComando++;
            }
        }
    }


    // Envia à ECU os comandos que estão na Lista
    private void enviaComandosECU() {

        if (listaComandos.size() != 0) {

            if (posComando < 0) {
                posComando = 0;
            }

            String send = listaComandos.get(posComando);
            enviaMsgECU(send);

            if (posComando >= listaComandos.size() - 1) {
                posComando = 0;
            } else {
                posComando++;
            }
        }
    }


    // Quando envia um comando o hardware retorna esses dados abaixo + os dados do PID informado. Este método limpa o excesso e
    // só deixa o retorno do PID.
    private String limpaMsg(Message msg) {
        String tmpmsg = msg.obj.toString();

        tmpmsg = tmpmsg.replace("null", "");
        tmpmsg = tmpmsg.replaceAll("\\s", ""); //removes all [ \t\n\x0B\f\r]
        tmpmsg = tmpmsg.replaceAll(">", "");
        tmpmsg = tmpmsg.replaceAll("SEARCHING...", "");
        tmpmsg = tmpmsg.replaceAll("ATZ", "");
        tmpmsg = tmpmsg.replaceAll("ATI", "");
        tmpmsg = tmpmsg.replaceAll("atz", "");
        tmpmsg = tmpmsg.replaceAll("ati", "");
        tmpmsg = tmpmsg.replaceAll("ATDP", "");
        tmpmsg = tmpmsg.replaceAll("atdp", "");
        tmpmsg = tmpmsg.replaceAll("ATRV", "");
        tmpmsg = tmpmsg.replaceAll("atrv", "");

        return tmpmsg;
    }


    //ESTE MÉTODO RECEBE O OBJETO DO HANDLER E FAZ A LEITURA DOS DADOS QUE SÃO ENVIADOS PELO HARDWARE OBD2
    private void analisaMsg(Message msg) {

        String tmpmsg = limpaMsg(msg);

        voltBateria(tmpmsg);

        nomeVersaoOBD2(tmpmsg);

        if (!inicializado) {
            //ENVIA OS COMANDOS BASE DE INICIALIZAÇÃO DO HARDWARE
            enviaComandoInicializacao();
        } else {
            try {
                analysPIDS(tmpmsg);
            } catch (Exception e) {

            }

            // CONTINUA ENVIADO OS COMANDOS À UCE, QUANDO "inicializado" for true
            enviaComandosECU();

        }
    }


    // Obtem o nome do hardware e o protocolo que está sendo usado
    private void nomeVersaoOBD2(String tmpmsg) {

        if (tmpmsg.contains("ELM") || tmpmsg.contains("elm")) {
            nomeOBD2 = tmpmsg;
        }

        if (tmpmsg.contains("SAE") || tmpmsg.contains("ISO")
                || tmpmsg.contains("sae") || tmpmsg.contains("iso") || tmpmsg.contains("AUTO")) {
            protocoloUsado = tmpmsg;
        }

        if (protocoloUsado != null && nomeOBD2 != null) {
            nomeOBD2 = nomeOBD2.replaceAll("STOPPED", "");
            protocoloUsado = protocoloUsado.replaceAll("STOPPED", "");
            Status.setText(nomeOBD2 + "  " + protocoloUsado);
        }
    }


    // RECEBE OS DADOS DO HANDLER E TRANSFORMA PARA DECIMAL
    private void analysPIDS(String dadosRecebidos) throws InterruptedException {

        int A;
        int B;
        int PID;

        if ((dadosRecebidos != null) && (dadosRecebidos.matches("^[0-9A-F]+$"))) {

            dadosRecebidos = dadosRecebidos.trim();

            int index = dadosRecebidos.indexOf("41");

            String tmpmsg;

            if (index != -1) {

                tmpmsg = dadosRecebidos.substring(index);

                // Transformar os dados que estão em hexadecimal para decimal
                if (tmpmsg.substring(0, 2).equals("41")) {
                    PID = Integer.parseInt(tmpmsg.substring(2, 4), 16);
                    A = Integer.parseInt(tmpmsg.substring(4, 6), 16);
                    B = Integer.parseInt(tmpmsg.substring(6, 8), 16);

                    calculaValoresECU(PID, A, B);
                }
            }
        }
    }

    //Bateria do Motor
    private void voltBateria(String msg) {

        String voltTexto = null;

        if ((msg != null) && (msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})\\s*"))) {

            voltTexto = msg + "V";


        } else if ((msg != null) && (msg.matches("\\s*[0-9]{1,2}([.][0-9]{1,2})?V\\s*"))) {

            voltTexto = msg;


        }

        if (voltTexto != null) {

            voltage.setText(voltTexto);
        }
    }


    // De acordo com O PID recebido inseri os valores de conversão, conforme o manual do hardware OBD2
    private void calculaValoresECU(int PID, int A, int B) throws InterruptedException {

        double val = 0;
        int intval = 0;
        int tempC = 0;

        switch (PID) {

            case 4://PID(04): Engine Load - Carga do Motor

                // A*100/255
                val = A * 100 / 255;
                int calcLoad = (int) val;

                if (valRpm > 0) {
                    cargaMotor.setText(Integer.toString(calcLoad) + " %");
                }

                break;

            case 5://PID(05): Temperatura da Água

                // A-40
                tempC = A - 40;
                valTemAgua = tempC;

                //  SIMULANDO UM ALERTA AO USUÁRIO CASO O CARRO ESTEJA ACIMA DA TEMPERATURA
                if (valTemAgua >= 60) {

                    tempAgua.setText(Integer.toString(valTemAgua) + " C°");

                    Animation anim = new AlphaAnimation(0.0f, 1.0f);
                    anim.setDuration(1000);
                    anim.setStartOffset(20);
                    anim.setRepeatMode(Animation.REVERSE);
                    anim.setRepeatCount(Animation.INFINITE);
                    textoTempAgua.startAnimation(anim);
                    tempAgua.startAnimation(anim);

                    textoTempAgua.setTextColor(Color.RED);
                    textoTempAgua.setTextSize(18);
                    textoTempAgua.setText("Veículo Aquecendo: ");
                    tempAgua.setTextColor(Color.RED);
                    tempAgua.setTextSize(18);


                } else {
                    tempAgua.setText(Integer.toString(valTemAgua) + " C°");

                }


                break;

            case 12: //PID(0C): RPM

                //((A*256)+B)/4
                val = ((A * 256) + B) / 4;
                intval = (int) val;
                valRpm = intval;

                rotacaoMotor.setText(Integer.toString(valRpm) + " RPM");

                break;


            case 15://PID(0F): Temperatura do ar

                // A - 40
                tempC = A - 40;
                valTemAr = tempC;
                tempAr.setText(Integer.toString(valTemAr) + " C°");

                break;


            default:
                Toast.makeText(getApplicationContext(), "PID " + PID + " NÃO ENCONTRADO", Toast.LENGTH_LONG).show();
        }
    }


}