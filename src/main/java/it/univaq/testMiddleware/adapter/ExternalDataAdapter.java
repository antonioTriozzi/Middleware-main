package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.models.*;

public interface ExternalDataAdapter {
    ExternalDataResponse fetchAndMapData(Condominio condominio, Dispositivo dispositivo, User user);
}
