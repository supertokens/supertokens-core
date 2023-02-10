package io.supertokens.webserver.api.dashboard;

import java.io.Serial;

import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.webserver.WebserverAPI;

public class VerifySessionAPI extends WebserverAPI{

    @Serial
    private static final long serialVersionUID = -3243992629116144574L;

    public VerifySessionAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/verify";
    }

    
    
}
