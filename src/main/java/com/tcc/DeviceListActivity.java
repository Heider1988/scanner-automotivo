package com.tcc;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;

/**
 * Classe que exibi os dipositivos pareados, pega o MAC e o nome e retorna o MAC do dispositivo
 */
public class DeviceListActivity extends Activity {

    // Returna Intent com o MAC do dispositivo
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    //Obtem informações do hardware
    private BluetoothAdapter mBtAdapter;

    //Array para armazenar os dispositivos pareados
    private ArrayAdapter<String> dispositivosPareados;


    // Ao clicar no dispositivo pareado obtem os dados e remove os pontos do MAC e passa o MAC como parâmetro
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            // PEGA O MAC ADDRESS DO HARDWARE
            String info = ((TextView) v).getText().toString();

            // DEIXA APENAS O ENDEREÇO MAC
            String address = info.substring(info.length() - 17);

            // Cria uma Intent Result e inclui o Mac
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            //Chama o método setResult e passa como parâmetro o MAC do dispositivo e depois finaliza
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configura para exibir os dispositivos pareados
        setContentView(R.layout.lista_dispositivo);

        // Initializa o array com os dispositivos pareados
        dispositivosPareados = new ArrayAdapter<>(this, R.layout.nome_dispositivo);


        // Exibi os dispositivos pareados e set eles no Array
        ListView viewListaPareados = findViewById(R.id.dispositivos_pareados);
        viewListaPareados.setAdapter(dispositivosPareados);
        viewListaPareados.setOnItemClickListener(mDeviceClickListener);


        // Bluetooth adapter local
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Busca dispositivos pareados
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // Se há dispositivos pareados, então pega o nome e o MAC e coloca no array de dispositivos
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                dispositivosPareados.add(device.getName() + "\n" + device.getAddress());
            }
        }
    }


}
