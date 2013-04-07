package jurnal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gdata.client.photos.PicasawebService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.media.MediaStreamSource;
import com.google.gdata.data.photos.AlbumEntry;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.data.photos.UserFeed;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

@SuppressWarnings("serial")
public class JurnalServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(JurnalServlet.class
            .getName());

    private enum RequestParameter {
        USER_NAME,
        ALBUM_NAME,
        ALBUM_ID,
        PHOTO_TITLE,
        PHOTO_CAPTION,
        PASSWORD,
        AUTHSUB,
        GPS_LAT,
        GPS_LONG,
    }

    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        Map<RequestParameter, String> params = Maps.newHashMap();

        // Decode parameters
        InputStream photoStream;
        try {
            photoStream = getParameters(req, params);
        } catch (FileUploadException e) {
            error(res, "Cannot decode POST parameters", e);
            return;
        }

        // Authenticate user.
        PicasawebService myService;
        try {
            myService = getAuthenticatedService(params);
        } catch (AuthenticationException e) {
            error(res, "Cannot authenticate user", e);
            return;
        }

        // Get album id by name.
        try {
            String albumId = getAlbumId(myService, params.get(RequestParameter.USER_NAME),
                    params.get(RequestParameter.ALBUM_NAME));
            params.put(RequestParameter.ALBUM_ID, albumId);
        } catch (ServiceException e) {
            error(res, "Album not found.", e);
            return;
        }

        // Construct photo entry.
        PhotoEntry photo = buildPhoto(params, photoStream);

        // Insert photo.
        try {
            insertPhoto(myService, params.get(RequestParameter.USER_NAME),
                    params.get(RequestParameter.ALBUM_ID), photo);
        } catch (ServiceException e) {
            error(res, "Cannot insert photo", e);
            return;
        }

        res.sendRedirect("/done.html");
    }

    /** Informs both the developers and the users about an error. */
    private void error(HttpServletResponse resp, String msg, Throwable e)
            throws IOException {
        resp.setContentType("text/plain");
        log.log(Level.SEVERE, msg, e);
        resp.sendError(500, msg);
    }

    /**
     * Constructs a PhotoEntry to be inserted in the album.
     */
    private PhotoEntry buildPhoto(Map<RequestParameter, String> params, InputStream file) {
        PhotoEntry myPhoto = new PhotoEntry();
        myPhoto.setTitle(new PlainTextConstruct(params.get(RequestParameter.PHOTO_TITLE)));
        myPhoto.setDescription(new PlainTextConstruct(params.get(RequestParameter.PHOTO_CAPTION)));
        myPhoto.setClient("ctalau-jurnal-1");

        myPhoto.setMediaSource(new MediaStreamSource(file, "image/jpeg"));

        if (params.containsKey(RequestParameter.GPS_LAT) &&
                params.containsKey(RequestParameter.GPS_LONG)) {
            myPhoto.setGeoLocation(
                    Double.valueOf(params.get(RequestParameter.GPS_LAT)),
                    Double.valueOf(params.get(RequestParameter.GPS_LONG)));
        }

        return myPhoto;
    }

    /** Inserts a PhotoEntry inside an album. */
    private void insertPhoto(PicasawebService myService, String userId,
            String albumId, PhotoEntry photo) throws IOException,
            ServiceException {
        URL albumPostUrl = new URL(
                "https://picasaweb.google.com/data/feed/api/user/" + userId
                        + "/albumid/" + albumId);
        myService.insert(albumPostUrl, photo);
    }

    /** Authenticates the current user to the PicasawebService. */
    private PicasawebService getAuthenticatedService(Map<RequestParameter, String> params)
            throws AuthenticationException {
        if (params.containsKey(RequestParameter.AUTHSUB)) {
        } else if (params.containsKey(RequestParameter.PASSWORD)) {
            PicasawebService myService = new PicasawebService("ctalau-jurnal-1");
            myService.setUserCredentials(params.get(RequestParameter.USER_NAME),
                    params.get(RequestParameter.PASSWORD));
            return myService;
        }
        throw new AuthenticationException(params.get(RequestParameter.USER_NAME));
    }

    /** Find the Id of an album with a name. */
    private String getAlbumId(PicasawebService myService, String userName,
            String albumName) throws IOException, ServiceException {
        URL feedUrl = new URL(
                "https://picasaweb.google.com/data/feed/api/user/" + userName
                        + "?kind=album");
        UserFeed myUserFeed = myService.getFeed(feedUrl, UserFeed.class);

        for (AlbumEntry myAlbum : myUserFeed.getAlbumEntries()) {
            if (myAlbum.getTitle().getPlainText().equals(albumName)) {
                return myAlbum.getGphotoId();
            }
        }

        throw new NoSuchElementException(albumName);
    }

    /** Parses the request parameters. */
    @SuppressWarnings("unused")
    private InputStream getParameters(HttpServletRequest req,
            Map<RequestParameter, String> params) throws FileUploadException, IOException {
        InputStream photo = null;
        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator iterator = upload.getItemIterator(req);
        while (iterator.hasNext()) {
            FileItemStream item = iterator.next();
            if (item.isFormField()) {
                params.put(RequestParameter.valueOf(item.getFieldName().toUpperCase()),
                        Streams.asString(item.openStream()));
            } else {
                byte[] bytes = ByteStreams.toByteArray(item.openStream());
                photo = new ByteArrayInputStream(bytes);
                params.put(RequestParameter.PHOTO_TITLE, item.getName());
            }
        }

        if (false) {
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equals(RequestParameter.AUTHSUB.toString())) {
                    params.put(RequestParameter.AUTHSUB, cookie.getValue());
                } else if (cookie.getName().equals(RequestParameter.PASSWORD.toString())) {
                    params.put(RequestParameter.PASSWORD, cookie.getValue());
                }
            }
        }

        return photo;
    }
}
