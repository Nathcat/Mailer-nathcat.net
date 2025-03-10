package com.nathcat.Mailer;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class Main {
    private static JSONObject readConfigFile(String path) {
        JSONObject config;

        try (FileInputStream fis = new FileInputStream(path)) {
            config = (JSONObject) new JSONParser().parse(new String(fis.readAllBytes()));

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }

        return config;
    }

    private static String formatString(String s, ResultSet rs) throws SQLException {
        String[] sA = s.split("\\$");
        for (int i = 1; i < sA.length; i += 2) {
            sA[i] = rs.getString(sA[i]);
        }

        StringBuilder sb = new StringBuilder();
        for (String S : sA) {
            sb.append(S);
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        JSONObject config = readConfigFile("/home/ubuntu/Mailer-nathcat.net/Mailer.conf.json");
        String sender = (String) config.get("mail-sender");
        String password = (String) config.get("mail-password");
        String host = (String) config.get("smtp-host");

        // Getting system properties
        Properties properties = System.getProperties();

        // Setting up mail server
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.port", "587");
        properties.setProperty("mail.smtp.starttls.enable", "true");

        // creating session object to get properties
        Session session = Session.getDefaultInstance(properties);


        try {
            // Get all emails to be sent
            Connection conn = DriverManager.getConnection((String) config.get("db-url"), (String) config.get("db-username"), (String) config.get("db-password"));
            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
            stmt.execute("SELECT MailToSend.*, SSO.Users.email AS 'email', SSO.Users.fullName AS 'fullName', SSO.Users.username AS 'username' FROM MailToSend LEFT JOIN SSO.Users on MailToSend.recipient = SSO.Users.id");
            ResultSet rs = stmt.getResultSet();

            Transport tr = session.getTransport("smtp");
            tr.connect(host, sender, password);

            while (rs.next()) {
                System.out.println(rs.getString("email"));
                // MimeMessage object.
                MimeMessage message = new MimeMessage(session);

                // Set From Field: adding senders email to from field.
                message.setFrom(new InternetAddress(sender));

                // Set To Field: adding recipient's email to from field.
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(rs.getString("email")));

                // Set Subject: subject of the email
                message.setSubject(formatString(rs.getString("subject"), rs));

                // set body of the email.
                message.setContent(formatString(rs.getString("content"), rs), "text/html");

                // Send email.
                message.saveChanges();
                try {
                    tr.sendMessage(message, message.getAllRecipients());
                    System.out.println("Sent email id: " + rs.getInt("id"));
                } catch (MessagingException mex) {
                    System.err.println("Failed to send email id: " + rs.getInt("id"));
                    mex.printStackTrace();
                }
            }

            tr.close();
            stmt.execute("DELETE FROM MailToSend");
            stmt.close();
            conn.close();
        }
        catch (MessagingException mex) {
            mex.printStackTrace();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}