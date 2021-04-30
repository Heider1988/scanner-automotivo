package com.tcc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Esta classe faz todo o trabalho para configurar e gerenciar o Bluetooth com outros dispositivos.
 */
public class BluetoothService {

    // Constantes que indicam o estado atual da conexão
    public static final int STATE_NONE = 0;       // parado
    public static final int STATE_LISTEN = 1;     // ouvindo conexões recebidas
    public static final int STATE_CONNECTING = 2; // iniciando uma conexão de saída
    public static final int STATE_CONNECTED = 3;  // conectado a um dispositivo remoto

    private static final String TAG = "BluetoothService";

    // CÓDIGO PARA FAZER A PONTE ENTRE OS DISPOSITIVOS
    private static final UUID MY_UUID = UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");

    // Representa o adaptador Bluetooth do dispositivo local
    public final BluetoothAdapter mAdapter;

    // Um manipulador permite enviar e processar Message objetos Runnable associados a um encadeamento MessageQueue
    private final Handler mBTHandler;

    // Thread para conectar e Thread para conectado
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    // Estado da aplicação
    private int mState;

    /**
     * Constructor. Prepara uma nova sessão.
     *
     * @param handler Um Handler para enviar mensagens de volta para a Activity da interface do usuário
     */
    public BluetoothService(Handler handler) {

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mBTHandler = handler;
    }

    /**
     * Returna o estado atual da conexão.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * define o estado atual da conexão entre os dispositivos
     *
     * @param state Um número inteiro que define o estado atual da conexão
     */
    private synchronized void setState(int state) {
        mState = state;

        //Novo estado ao handle para que a Activity da interface do usuário possa ser atualizada
        mBTHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Inicie a conexão entre os dispositivos. Especificamente AcceptThread para iniciar uma sessão no modo de escuta (servidor).
     * Chamado pela Activity onResume ()
     */
    public synchronized void start() {
        // Cancele qualquer thread que esteja tentando fazer uma conexão
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        //Cancele qualquer thread que esteja executando uma conexão
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }


        setState(STATE_LISTEN);
    }

    /**
     * Inicie a ConnectThread para iniciar uma conexão com um dispositivo remoto.
     *
     * @param device Dispositivo Bluetooth para conectar
     */
    public synchronized void connect(BluetoothDevice device) {

        // Cancele qualquer thread que esteja tentando fazer uma conexão
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        //Cancele qualquer thread que esteja executando uma conexão
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Inicie a thread para conectar-se ao dispositivo fornecido
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Inicie o ConnectedThread para começar a gerenciar uma conexão Bluetooth
     *
     * @param socket BluetoothSocket no qual a conexão foi feita
     */
    public synchronized void connected(BluetoothSocket socket) {

        // Cancele qualquer thread que esteja tentando fazer uma conexão
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        //Cancele qualquer thread que esteja executando uma conexão
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        //Inicie a thread para gerenciar a conexão e realizar transmissões
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();


        setState(STATE_CONNECTED);
    }

    /**
     * para todas as threads
     */
    public synchronized void stop() {

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }


        setState(STATE_NONE);
    }

    /**
     * Grave os dados recebidos da MainAcitivity no ConnectedThread
     *
     * @param out Os bytes a serem gravados
     */
    public void write(byte[] out) {
        // Cria um objeto temporário
        ConnectedThread r;
        // Sincronize uma cópia do ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) {
                return;
            }
            r = mConnectedThread;
        }
        // Executa a gravação
        r.write(out);
    }


    /**
     * Essa thread é executada ao tentar fazer uma conexão de saída com um dispositivo.
     */
    private class ConnectThread extends Thread {
        public final BluetoothSocket mmSocket;
        public final BluetoothDevice mmDevice;

        // Construtor recebe um device como parâmetro
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Obtenha um BluetoothSocket para uma conexão com o dispositivo Bluetooth fornecido
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Erro: ", e);
            }
            mmSocket = tmp;
        }

        public void run() {

            // Conexão com o BluetoothSocket
            try {
                //Esta é uma chamada de bloqueio e retornará apenas uma conexão ou exceção.
                mmSocket.connect();
            } catch (IOException e) {
                // Fecha o socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Erro ao chamar o método close(). Falha na conexão durante a chamada do socket", e2);
                }
                return;
            }

            // Redefina o ConnectThread porque terminamos
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Iniciar o thread conectado
            connected(mmSocket);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Método close() do socket de conexão falhou: ", e);
            }
        }
    }

    /**
     * Esse thread é executada durante uma conexão com um dispositivo remoto.
     * Ele lida com todas as transmissões de entrada e saída.
     */
    private class ConnectedThread extends Thread {

        // Socket para fazer a ponte entre os dispositivos
        private final BluetoothSocket mmSocket;

        //Entrada e saída de bytes
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        String s, msg;

        //Construtor recebendo um socket
        public ConnectedThread(BluetoothSocket socket) {

            mmSocket = socket;

            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Obtenha os fluxos de entrada e saída do BluetoothSocket
            try {
                //Entrada
                tmpIn = socket.getInputStream();
                //Saída
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "soquetes temporários não criados", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

            while (true) {
                // Toda mensagem de resposta do hardware obd2 retorna no final o caractere ">" para informar que acabou a mensagem.
                // Enquanto não chegar no final, a String msg vai recebendo os dados, quando chegar envia tudo para o handler.
                try {
                    byte[] buffer = new byte[1];
                    mmInStream.read(buffer, 0, buffer.length);
                    s = new String(buffer);
                    for (int i = 0; i < s.length(); i++) {
                        char x = s.charAt(i);
                        msg = msg + x;
                        // Se chegou no final, então envia a mensagem para o handler
                        if (msg.contains(">")) {
                            mBTHandler.obtainMessage(MainActivity.MESSAGE_READ, buffer.length, -1, msg).sendToTarget();
                            msg = "";
                        }
                    }
                } catch (IOException e) {
                    // Inicie o serviço novamente para reiniciar o modo de escuta
                    BluetoothService.this.start();
                    break;
                }
            }
        }

        // Este é o método núcleo, tudo passa por ele. Ele que escreve os PIDs do hardware ELM327
        public void write(byte[] buffer) {
            try {

                byte[] arrayOfBytes = buffer;
                mmOutStream.write(arrayOfBytes);
                //Libera o fluxo de saída e força a gravação de quaisquer bytes de saída em buffer.
                mmOutStream.flush();

            } catch (IOException e) {
                Log.e(TAG, "Erro durante a escrita dos Pids", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Erro ao invocar o método close()", e);
            }
        }
    }

}
