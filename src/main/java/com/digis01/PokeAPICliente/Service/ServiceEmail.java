package com.digis01.PokeAPICliente.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class ServiceEmail {

    @Autowired
    private JavaMailSender javaMailSender;

    //Mandar correo de restablecimiento de contraseña
    public void sendPasswordResetEmail(String toEmail, String token) {

        String subject = "Recuperacion de contraseña";
        String url = "http://localhost:8080/login/restablecerContrasenia?token=" + token;

//        String body = "<html>"
//                + "<body style=\"font-family: Arial, sans-serif; color: #333; background-color: red;\">"
//                + "<p style=\"font-size: 16px;\">Hemos recibido una solicitud de recuperación de contraseña.</p>"
//                + "<p style=\"font-size: 16px;\">Haz clic en el siguiente botón para restablecer tu contraseña:</p>"
//                + "<div style=\"text-align: center; margin-top: 20px;\">"
//                + "<a href=\"" + url + "\" style=\"text-decoration: none;\">"
//                + "<button style=\""
//                + "background-color: #4CAF50; "
//                + "color: white; "
//                + "padding: 15px 32px; "
//                + "text-align: center; "
//                + "text-decoration: none; "
//                + "display: inline-block; "
//                + "font-size: 16px; "
//                + "border: none; "
//                + "border-radius: 4px; "
//                + "cursor: pointer; "
//                + "transition: background-color 0.3s;\">"
//                + "Restablecer contraseña"
//                + "</button>"
//                + "</a>"
//                + "</div>"
//                + "<p style=\"font-size: 14px; margin-top: 30px;\">Si no solicitaste este cambio, por favor ignora este mensaje.</p>"
//                + "</body>"
//                + "</html>";
        String body = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "    <body>\n"
                + "        <div class=\"container\" style=\"justify-content: center; align-items: center; width: 400px;\">\n"
                + "            <div class=\"head\" style=\"height: 50px; background-color: #383838; border-radius: 10px 10px 0 0;\"></div>\n"
                + "\n"
                + "            <center>\n"
                + "                <div class=\"emailbody\">\n"
                + "                    <div class=\"header\" style=\"border: 1px solid; margin: 20px;\">\n"
                + "                        <h3 style=\"text-transform: uppercase;\">Solicitud de recuperación de contraseña</h3>\n"
                + "                    </div>\n"
                + "                    <p>Hemos recibido una solicitud de recuperacion de contraseña, haz clic en el siguiente enlace para restablecer tu contraseña.</p>\n"
                + "                    <p>Si tu no has hecho esto, por favor ignora este mensaje.</p>\n"
                + "\n"
                + "                    <div style=\"width: 250px; height: 40px; background-color: green;\">\n"
                + "                        <a href=\"" + url + "\" class=\"btn btn-success\" style=\"text-transform: uppercase; border-radius: 0; width: 250px; height: 40px; background-color: green; text-decoration: none; color: white\">Restablecer contraseña</a>\n"
                + "                    </div>\n"
                + "                    <br>\n"
                + "                    <br>\n"
                + "                </div>\n"
                + "\n"
                + "                <div class=\"footer\" style=\"height: 50px; background-color: #383838; border-radius: 0 0 10px 10px;\"></div>\n"
                + "            </center>\n"
                + "\n"
                + "        </div>\n"
                + "    </body>\n"
                + "</html>\n"
                + "";

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("no-reply@tudominio.com"); //Cambia el remitente si es necesario
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true); //true para enviar el html

            javaMailSender.send(message);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    //Mandar correo del cambio de contraseña exitoso
    public void sendPasswordChangedNotification(String toEmail, String nombreUsuario) {
        String subject = "Tu contraseña ha sido cambiada";
//        String body = "<html>"
//                + "<body style=\"font-family: Arial, sans-serif; color: #333;\">"
//                + "<p>Hola " + nombreUsuario + ",</p>"
//                + "<p>Tu contraseña ha sido cambiada exitosamente.</p>"
//                + "<p>Si no realizaste este cambio, contacta al soporte de inmediato.</p>"
//                + "<p>Saludos,<br/>El equipo de soporte</p>"
//                + "</body>"
//                + "</html>";

        String body = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "    <body>\n"
                + "        <div class=\"container\" style=\"justify-content: center; align-items: center; width: 400px;\">\n"
                + "            <div class=\"head\" style=\"height: 50px; background-color: #383838; border-radius: 10px 10px 0 0;\"></div>\n"
                + "\n"
                + "            <center>\n"
                + "                <div class=\"emailbody\">\n"
                + "                    <div class=\"header\" style=\"border: 1px solid; margin: 20px;\">\n"
                + "                        <h3 style=\"text-transform: uppercase;\">Cambio de contraseña exitoso</h3>\n"
                + "                    </div>\n"
                + "                    <p>Hola " + nombreUsuario + "! Tu contraseña ha sido modificada exitosamente.</p>\n"
                + "                    <p>Si no realizaste este cambio, contacta a soporte de inmediato.</p>\n"
                + "\n"
                + "                    <br>\n"
                + "                    <br>\n"
                + "                </div>\n"
                + "\n"
                + "                <div class=\"footer\" style=\"height: 50px; background-color: #383838; border-radius: 0 0 10px 10px;\"></div>\n"
                + "            </center>\n"
                + "\n"
                + "        </div>\n"
                + "    </body>\n"
                + "</html>\n"
                + "";

        try {

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("no-reply@tudominio.com"); //Cambia el remitente si es necesario
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true); //true para enviar el html

            javaMailSender.send(message);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //Mandar correo de creacion de cuenta
    public void sendNewUserEmail(String toEmail, String nombre, String password) {

        String subject = "Bienvenido a la plataforma";
        String url = "http://localhost:8080/login";
//        String body = "<html><body>"
//                + "<p>Hola " + nombre + ",</p>"
//                + "<p>Tu cuenta ha sido creada exitosamente con este correo. Ingresa a la siguente liga para iniciar sesión.</p>"
//                + "<div style=\"text-align: center; margin-top: 20px;\">"
//                + "<a href=\"" + url + "\" style=\"text-decoration: none;\">"
//                + "<button style=\""
//                + "background-color: #4CAF50; "
//                + "color: white; "
//                + "padding: 15px 32px; "
//                + "text-align: center; "
//                + "text-decoration: none; "
//                + "display: inline-block; "
//                + "font-size: 16px; "
//                + "border: none; "
//                + "border-radius: 4px; "
//                + "cursor: pointer; "
//                + "transition: background-color 0.3s;\">"
//                + "Iniciar sesión"
//                + "</button>"
//                + "</a>"
//                + "</div>"
//                + "</body></html>";

        String body = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "    <body>\n"
                + "        <div class=\"container\" style=\"justify-content: center; align-items: center; width: 400px;\">\n"
                + "            <div class=\"head\" style=\"height: 50px; background-color: #383838; border-radius: 10px 10px 0 0;\"></div>\n"
                + "\n"
                + "            <center>\n"
                + "                <div class=\"emailbody\">\n"
                + "                    <div class=\"header\" style=\"border: 1px solid; margin: 20px;\">\n"
                + "                        <h3 style=\"text-transform: uppercase;\">Cuenta creada exitosamente</h3>\n"
                + "                    </div>\n"
                + "                    <p>Hola!.</p>\n"
                + "                    <p>Tu cuenta ha sido creada exitosamente con este correo. Ingresa a la siguiente liga para iniciar sesión.</p>\n"
                + "                    <p>username: " + nombre + "</p>\n"
                + "                    <p>password:  " + password + "</p>\n"
                + "                    <p>Si tu no has hecho esto, por favor ignora este mensaje.</p>\n"
                + "\n"
                + "                    <div style=\"width: 250px; height: 40px; background-color: green;\">\n"
                + "                        <a href=\"" + url + "\" class=\"btn btn-success\" style=\"text-transform: uppercase; border-radius: 0; width: 250px; height: 40px; background-color: green; text-decoration: none; color: white\">Iniciar sesión</a>\n"
                + "                    </div>\n"
                + "                    <br>\n"
                + "                    <br>\n"
                + "                </div>\n"
                + "\n"
                + "                <div class=\"footer\" style=\"height: 50px; background-color: #383838; border-radius: 0 0 10px 10px;\"></div>\n"
                + "            </center>\n"
                + "\n"
                + "        </div>\n"
                + "    </body>\n"
                + "</html>\n"
                + "";

        try {

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("no-reply@tudominio.com"); //Cambia el remitente si es necesario
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true); //true para enviar el html

            javaMailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Mandar correo para iniciar sesión
    public void sendLoginVerificationEmail(String toEmail, String nombre, String url) {
        String subject = "Validación de inicio se sesión";

//        String body = "<html><body>"
//                + "<p>Hola " + nombre + ",</p>"
//                + "<p>Confirma tu inicio de sesión dando clic en el botón de abajo:</p>"
//                + "<div style='text-align:center;margin-top:20px;'>"
//                + "<a href='" + url + "'>"
//                + "<button style='background-color:#4CAF50;color:white;"
//                + "padding:15px 32px;border:none;border-radius:4px;cursor:pointer;'>"
//                + "Confirmar inicio de sesión"
//                + "</button></a></div>"
//                + "</body></html>";
        String body = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "  <body style=\"margin:0; padding:0; font-family:Arial, Helvetica, sans-serif; background:#f6f6f6;\">\n"
                + "    <center style=\"width:100%; padding:20px 0;\">\n"
                + "\n"
                + "      <table cellpadding=\"0\" cellspacing=\"0\" width=\"420\" style=\"background:#fff; border-radius:14px; box-shadow:0 6px 18px rgba(0,0,0,0.15); overflow:hidden;\">\n"
                + "\n"
                + "        <!-- HEADER -->\n"
                + "        <tr>\n"
                + "          <td style=\"background:#ef4444; padding:22px; text-align:center;\">\n"
                + "            <div style=\"width:60px; height:60px; margin:0 auto; border-radius:50%; background:#fff; border:5px solid #000; position:relative;\">\n"
                + "              <div style=\"position:absolute; inset:0; margin:auto; width:18px; height:18px; border-radius:50%; background:#fff; border:3px solid #000;\"></div>\n"
                + "            </div>\n"
                + "            <h2 style=\"color:white; margin-top:12px; font-size:20px; font-weight:bold; letter-spacing:1px;\">AUTENTICACIÓN POKÉMON</h2>\n"
                + "          </td>\n"
                + "        </tr>\n"
                + "\n"
                + "        <!-- CUERPO -->\n"
                + "        <tr>\n"
                + "          <td style=\"padding:26px 32px;\">\n"
                + "            <h3 style=\"margin:0 0 10px; font-size:18px; color:#111;\">Hola " + nombre + "!</h3>\n"
                + "            <p style=\"font-size:14px; color:#444; line-height:1.5; margin:0 0 16px;\">\n"
                + "              Para confirmar tu inicio de sesión, presiona el siguiente botón:\n"
                + "            </p>\n"
                + "\n"
                + "            <!-- BOTÓN POKÉ -->\n"
                + "            <center>\n"
                + "              <a href=\"" + url + "\" \n"
                + "                 style=\"display:inline-block; background:#ef4444; color:#fff; text-decoration:none;\n"
                + "                        padding:14px 28px; border-radius:30px; font-size:15px; font-weight:bold;\n"
                + "                        border:3px solid #000; box-shadow:0 4px 0 #b91c1c; text-transform:uppercase;\">\n"
                + "                Autenticarme\n"
                + "              </a>\n"
                + "            </center>\n"
                + "\n"
                + "            <p style=\"font-size:12px; color:#777; margin-top:20px; text-align:center;\">\n"
                + "              Si no solicitaste este inicio de sesión, puedes ignorar este mensaje.\n"
                + "            </p>\n"
                + "          </td>\n"
                + "        </tr>\n"
                + "\n"
                + "        <!-- FOOTER -->\n"
                + "        <tr>\n"
                + "          <td style=\"background:#000; padding:16px; text-align:center; color:#bbb; font-size:12px;\">\n"
                + "            <p style=\"margin:0;\">PokéAPI · Entrenadores</p>\n"
                + "            <p style=\"margin:0;\">© 2025 Todos los derechos reservados</p>\n"
                + "          </td>\n"
                + "        </tr>\n"
                + "\n"
                + "      </table>\n"
                + "    </center>\n"
                + "  </body>\n"
                + "</html>";

        try {

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("no-reply@tudominio.com");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);

            javaMailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
