package me.proxycracked.universalaccountmanager.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public final class HttpUtils {
   private HttpUtils() {
   }

   public static String get(String url) throws Exception {
      CloseableHttpClient client = HttpClients.createDefault();
      Throwable var2 = null;

      String var4;
      try {
         HttpGet req = new HttpGet(url);
         req.setHeader("User-Agent", "UniversalAccountManager/1.0");
         var4 = EntityUtils.toString(client.execute(req).getEntity());
      } catch (Throwable var13) {
         var2 = var13;
         throw var13;
      } finally {
         if (client != null) {
            if (var2 != null) {
               try {
                  client.close();
               } catch (Throwable var12) {
                  var2.addSuppressed(var12);
               }
            } else {
               client.close();
            }
         }
      }

      return var4;
   }

   public static byte[] getBytes(String url) throws Exception {
      URL u = new URL(url);
      HttpURLConnection conn = (HttpURLConnection)u.openConnection();
      conn.setRequestProperty("User-Agent", "UniversalAccountManager/1.0");
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(15000);

      byte[] var8;
      try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         byte[] buf = new byte[8192];

         int n;
         while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
         }

         var8 = out.toByteArray();
      } finally {
         conn.disconnect();
      }

      return var8;
   }
}
