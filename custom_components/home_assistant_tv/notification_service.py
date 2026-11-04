"""Shared authorization for the explicit action and the HA notify entity."""
from homeassistant.exceptions import ServiceValidationError, Unauthorized


async def async_send_notification(hass, repository, data, context, *, dismiss=False):
    """Check the initiating user, not the receiver's account or a profile owner."""
    user_id = context.user_id if context else None
    user = await hass.auth.async_get_user(user_id) if user_id else None
    if user_id and (user is None or not user.is_active):
        raise Unauthorized()
    try:
        return repository.notifications.send(
            data, owner=user.id if user else None,
            admin=user.is_admin if user else True, dismiss=dismiss)
    except (ValueError, TypeError, OverflowError) as error:
        raise ServiceValidationError(str(error)) from error
