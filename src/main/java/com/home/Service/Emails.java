//package com.home.Service;
//
//import io.jsonwebtoken.io.IOException;
//
//public class Emails {
//	  public static void main(String[] args) throws IOException {
//		    Emails from = new Email("test@example.com");
//		    String subject = "Sending with SendGrid is Fun";
//		    Emails to = new Email("test@example.com");
//		    Content content = new Content("text/plain", "and easy to do anywhere, even with Java");
//		    Mail mail = new Mail(from, subject, to, content);
//
//		    SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
//		    Request request = new Request();
//		    try {
//		      request.setMethod(Method.POST);
//		      request.setEndpoint("mail/send");
//		      request.setBody(mail.build());
//		      Response response = sg.api(request);
//		      System.out.println(response.getStatusCode());
//		      System.out.println(response.getBody());
//		      System.out.println(response.getHeaders());
//		    } catch (IOException ex) {
//		      throw ex;
//		    }
//		  }
//		}