package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.models.Dispositivo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExternalDataAdapterFactory {

    @Autowired
    private ZcsAzzurroAdapter zcsAzzurroAdapter;

    @Autowired
    private RandomDataAdapter randomDataAdapter;

    @Autowired
    private InverterAdapter inverterAdapter; // <--- nuovo adapter

    public ExternalDataAdapter getAdapter(Dispositivo dispositivo) {
        if (dispositivo.getAdapterType() == null || dispositivo.getAdapterType().trim().isEmpty() ||
                dispositivo.getAdapterType().equalsIgnoreCase("default")) {
            System.out.println("Uso Random");
            return randomDataAdapter;
        }
        else if (dispositivo.getAdapterType().equalsIgnoreCase("zscazzurro")) {
            System.out.println("Uso ZcsAzzurro");
            return zcsAzzurroAdapter;
        }
        else if (dispositivo.getAdapterType().equalsIgnoreCase("inverter")) { // <--- riconoscimento Inverter
            System.out.println("Uso InverterAdapter");
            return inverterAdapter;
        }

        // Altrimenti, ritorna adapter di default
        System.out.println("Adapter non trovato, uso Random");
        return randomDataAdapter;
    }
}