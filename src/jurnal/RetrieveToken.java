package jurnal;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gdata.client.http.AuthSubUtil;
import com.google.gdata.client.photos.PicasawebService;

@SuppressWarnings("serial")
public class RetrieveToken extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String singleUseToken = AuthSubUtil.getTokenFromReply(req
                .getQueryString());
        PicasawebService picasa = new PicasawebService("Jurnal");
        picasa.setAuthSubToken(singleUseToken, null);

    }
}
