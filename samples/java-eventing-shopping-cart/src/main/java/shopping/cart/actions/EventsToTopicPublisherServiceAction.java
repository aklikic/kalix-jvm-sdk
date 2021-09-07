/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package shopping.cart.actions;

import com.akkaserverless.javasdk.action.ActionCreationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shopping.cart.domain.ShoppingCartDomain;

/** An action. */
public class EventsToTopicPublisherServiceAction extends AbstractEventsToTopicPublisherServiceAction {

  private static final Logger LOG = LoggerFactory.getLogger(EventsToTopicPublisherServiceAction.class);

  public EventsToTopicPublisherServiceAction(ActionCreationContext creationContext) {}

  /** Handler for "PublishAdded". */
  @Override
  public Effect<ShoppingCartDomain.ItemAdded> publishAdded(ShoppingCartDomain.ItemAdded itemAdded) {
    LOG.info("Publishing: '{}' to topic", itemAdded);
    return effects().reply(itemAdded);
  }
  /** Handler for "PublishRemoved". */
  @Override
  public Effect<ShoppingCartDomain.ItemRemoved> publishRemoved(ShoppingCartDomain.ItemRemoved itemRemoved) {
    LOG.info("Publishing: '{}' to topic", itemRemoved);
    return effects().reply(itemRemoved);
  }
  /** Handler for "PublishCheckedOut". */
  @Override
  public Effect<ShoppingCartDomain.CheckedOut> publishCheckedOut(ShoppingCartDomain.CheckedOut checkedOut) {
    LOG.info("Publishing: '{}' to topic", checkedOut);
    return effects().reply(checkedOut);
  }
}