package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2021 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Pair;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

// Brand Indicators for Message Identification (BIMI)
// https://bimigroup.org/

public class Bimi {
    // Beam me up, Scotty
    private static final int CONNECT_TIMEOUT = 10 * 1000; // milliseconds
    private static final int READ_TIMEOUT = 15 * 1000; // milliseconds
    private static final String OID_BrandIndicatorforMessageIdentification = "1.3.6.1.5.5.7.3.31";

    static Pair<Bitmap, Boolean> get(
            Context context, String domain, String selector, int scaleToPixels)
            throws IOException {
        Bitmap bitmap = null;
        boolean verified = false;

        if (TextUtils.isEmpty(selector))
            selector = "default";

        // Get DNS record
        DnsHelper.DnsRecord[] records;
        try {
            String txt = selector + "._bimi." + domain;
            Log.i("BIMI fetch TXT=" + txt);
            records = DnsHelper.lookup(context, txt, "txt");
            if (records.length == 0)
                return null;
            Log.i("BIMI got TXT=" + records[0].name);
        } catch (Throwable ex) {
            Log.i(ex);
            return null;
        }

        // Decode DNS record
        Map<String, String> values = new HashMap<>();
        String[] params = records[0].name.split(";");
        for (String param : params) {
            String[] kv = param.split("=");
            if (kv.length != 2)
                continue;
            values.put(kv[0].trim().toLowerCase(), kv[1].trim());
        }

        // Process DNS record
        List<String> tags = new ArrayList<>(values.keySet());
        Collections.sort(tags); // process certificate first
        for (String tag : tags) {
            switch (tag) {
                // Version
                case "v": {
                    String version = values.get(tag);
                    if (!"BIMI1".equalsIgnoreCase(version))
                        Log.w("BIMI unsupported version=" + version);
                    break;
                }

                // Image link
                case "l": {
                    if (bitmap != null)
                        continue;

                    String l = values.get(tag);
                    if (TextUtils.isEmpty(l))
                        continue;

                    URL url = new URL(l);
                    Log.i("BIMI favicon " + url);

                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(READ_TIMEOUT);
                    connection.setConnectTimeout(CONNECT_TIMEOUT);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
                    connection.connect();

                    try {
                        bitmap = ImageHelper.renderSvg(connection.getInputStream(),
                                Color.WHITE, scaleToPixels);
                    } finally {
                        connection.disconnect();
                    }

                    break;
                }

                // Certificate link
                case "a": {
                    if (verified)
                        continue;

                    String a = values.get(tag);
                    if (TextUtils.isEmpty(a))
                        continue;

                    try {
                        URL url = new URL(a);
                        Log.i("BIMI PEM " + url);

                        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setReadTimeout(READ_TIMEOUT);
                        connection.setConnectTimeout(CONNECT_TIMEOUT);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty("User-Agent", WebViewEx.getUserAgent(context));
                        connection.connect();

                        // Fetch PEM objects
                        List<PemObject> pems = new ArrayList<>();
                        try {
                            InputStreamReader isr = new InputStreamReader(connection.getInputStream());
                            PemReader reader = new PemReader(isr);
                            while (true) {
                                PemObject pem = reader.readPemObject();
                                if (pem == null)
                                    break;
                                else
                                    pems.add(pem);
                            }
                        } finally {
                            connection.disconnect();
                        }

                        if (pems.size() == 0)
                            throw new IllegalArgumentException("No PEM objects");

                        // Convert to X.509 certificates
                        List<X509Certificate> certs = new ArrayList<>();
                        CertificateFactory fact = CertificateFactory.getInstance("X.509");
                        for (PemObject pem : pems) {
                            ByteArrayInputStream bis = new ByteArrayInputStream(pem.getContent());
                            certs.add((X509Certificate) fact.generateCertificate(bis));
                        }

                        // Get first certificate
                        // https://datatracker.ietf.org/doc/draft-fetch-validation-vmc-wchuang/
                        X509Certificate cert = certs.remove(0);

                        // Check certificate type
                        List<String> eku = cert.getExtendedKeyUsage();
                        if (!eku.contains(OID_BrandIndicatorforMessageIdentification))
                            throw new IllegalArgumentException("Invalid certificate type");

                        // Check subject
                        if (!EntityCertificate.getDnsNames(cert).contains(domain))
                            throw new IllegalArgumentException("Invalid certificate domain");

                        // https://datatracker.ietf.org/doc/html/rfc3709#page-6
                        // LogotypeExtn ::= SEQUENCE {
                        //   subjectLogo     [2] EXPLICIT LogotypeInfo OPTIONAL,
                        //     LogotypeInfo ::= CHOICE {
                        //       direct          [0] LogotypeData,
                        //         LogotypeData ::= SEQUENCE {
                        //           image           SEQUENCE OF LogotypeImage OPTIONAL,
                        //             LogotypeImage ::= SEQUENCE {
                        //               imageDetails    LogotypeDetails,
                        //                 LogotypeDetails ::= SEQUENCE {
                        //                   mediaType       IA5String,
                        //                   logotypeHash    SEQUENCE SIZE (1..MAX) OF HashAlgAndValue,
                        //                   logotypeURI     SEQUENCE SIZE (1..MAX) OF IA5String }
                        try {
                            byte[] logoType = cert.getExtensionValue(Extension.logoType.getId());
                            ASN1Sequence logotypeExtn =
                                    (ASN1Sequence) (ASN1Sequence) JcaX509ExtensionUtils.parseExtensionValue(logoType);
                            for (int i = 0; i != logotypeExtn.size(); i++) {
                                ASN1TaggedObject subjectLogo = ASN1TaggedObject.getInstance(logotypeExtn.getObjectAt(i));
                                if (subjectLogo.getTagNo() == 2) {
                                    ASN1TaggedObject logotypeInfo = (ASN1TaggedObject) subjectLogo.getObject();
                                    if (logotypeInfo.getTagNo() == 0) {
                                        ASN1Sequence logotypeData = (ASN1Sequence) logotypeInfo.getObject();
                                        ASN1Sequence logotypeImage = (ASN1Sequence) logotypeData.getObjectAt(0);
                                        ASN1Sequence logotypeDetails = (ASN1Sequence) logotypeImage.getObjectAt(0);

                                        DERIA5String mediaType = (DERIA5String) logotypeDetails.getObjectAt(0);
                                        Log.i("BIMI media type=" + mediaType.getString());

                                        ASN1Sequence logotypeURI = (ASN1Sequence) logotypeDetails.getObjectAt(2);
                                        DERIA5String uri = (DERIA5String) logotypeURI.getObjectAt(0);
                                        Log.i("BIMI log uri=" + uri.getString());

                                        String mimeType = ImageHelper.getDataUriType(uri.getString());
                                        if ("image/svg+xml".equalsIgnoreCase(mimeType)) {
                                            InputStream is = ImageHelper.getDataUriStream(uri.getString());
                                            bitmap = ImageHelper.renderSvg(is, Color.WHITE, scaleToPixels);
                                            Log.i("BIMI URI image=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (Throwable ex) {
                            Log.e(ex);
                        }

                        // Get trust anchors
                        Set<TrustAnchor> trustAnchors = new HashSet<>();

                        // Get root certificates from assets
                        for (String ca : context.getAssets().list(""))
                            if (ca.endsWith(".pem")) {
                                Log.i("Reading ca=" + ca);
                                try (InputStream is = context.getAssets().open(ca)) {
                                    X509Certificate c = (X509Certificate) fact.generateCertificate(is);
                                    trustAnchors.add(new TrustAnchor(c, null));
                                }
                            }

                        // Get root certificates from key store
                        KeyStore ks = KeyStore.getInstance("AndroidCAStore");
                        ks.load(null, null);
                        Enumeration<String> aliases = ks.aliases();
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            Certificate c = ks.getCertificate(alias);
                            if (c instanceof X509Certificate)
                                trustAnchors.add(new TrustAnchor((X509Certificate) c, null));
                        }

                        // Validate certificate
                        X509CertSelector target = new X509CertSelector();
                        target.setCertificate(cert);

                        PKIXBuilderParameters pparams = new PKIXBuilderParameters(trustAnchors, target);
                        CertStoreParameters intermediates = new CollectionCertStoreParameters(certs);
                        pparams.addCertStore(CertStore.getInstance("Collection", intermediates));
                        pparams.setRevocationEnabled(false);
                        pparams.setDate(null);

                        CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
                        CertPathBuilderResult path = builder.build(pparams);

                        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
                        cpv.validate(path.getCertPath(), pparams);

                        Log.i("BIMI valid domain=" + domain);
                        verified = true;
                    } catch (Throwable ex) {
                        Log.w(new Throwable("BIMI " + domain, ex));
                    }

                    break;
                }

                default:
                    Log.w("Unknown BIMI tag=" + tag);
            }
        }

        return (bitmap == null ? null : new Pair<>(bitmap, verified));
    }
}