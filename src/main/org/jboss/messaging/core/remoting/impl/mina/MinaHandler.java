/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.core.remoting.impl.mina;

import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.reqres.Response;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.PacketDispatcher;
import org.jboss.messaging.core.remoting.PacketSender;
import org.jboss.messaging.core.remoting.RemotingException;
import org.jboss.messaging.core.remoting.impl.wireformat.AbstractPacket;
import org.jboss.messaging.core.remoting.impl.wireformat.Packet;
import org.jboss.messaging.core.remoting.impl.wireformat.Ping;
import org.jboss.messaging.core.server.MessagingException;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public class MinaHandler extends IoHandlerAdapter
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(MinaHandler.class);

   // Attributes ----------------------------------------------------

   private final PacketDispatcher dispatcher;

   private FailureNotifier failureNotifier;

   private boolean closeSessionOnExceptionCaught;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------
   
   public MinaHandler(PacketDispatcher dispatcher, FailureNotifier failureNotifier, boolean closeSessionOnExceptionCaught)
   {
      this.dispatcher = dispatcher;
      this.failureNotifier = failureNotifier;
      this.closeSessionOnExceptionCaught = closeSessionOnExceptionCaught;
   }

   // Public --------------------------------------------------------

   // IoHandlerAdapter overrides ------------------------------------

   @Override
   public void exceptionCaught(IoSession session, Throwable cause)
         throws Exception
   {
      log.error("caught exception " + cause + " for session " + session, cause);
      
      if (failureNotifier != null)
      {
         String serverSessionID = Long.toString(session.getId());
         RemotingException re = new RemotingException(MessagingException.INTERNAL_ERROR, "unexpected exception", serverSessionID);
         re.initCause(cause);
         failureNotifier.fireFailure(re);
      }
      if (closeSessionOnExceptionCaught)
      {
         session.close();
      }
   }
   
   @Override
   public void messageReceived(final IoSession session, Object message)
         throws Exception
   {
      if (message instanceof Response)
      {
         log.trace("received response " + message);
         // response is handled by the reqres filter.
         // do nothing
         return;
      }
      
      if (message instanceof Ping)
      {
         log.trace("received ping " + message);
         // response is handled by the keep-alive filter.
         // do nothing
         return;
      }

      if (!(message instanceof AbstractPacket))
      {
         throw new IllegalArgumentException("Unknown message type: " + message);
      }

      AbstractPacket packet = (AbstractPacket) message;
      PacketSender sender = new PacketSender()
      {
         public void send(Packet p) throws Exception
         {
            dispatcher.callFilters(p);
            session.write(p);            
         }
         
         public String getSessionID()
         {
            return Long.toString(session.getId());
         }
         
         public String getRemoteAddress()
         {
            return session.getRemoteAddress().toString();
         }
      };

      if (log.isTraceEnabled())
         log.trace("received packet " + packet);

      dispatcher.dispatch(packet, sender);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
