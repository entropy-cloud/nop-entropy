export function toast(_event, _props, page) {
  const action = page?.getAction?.('preview.notify');

  if (typeof action === 'function') {
    return action();
  }

  return {
    status: -1,
    msg: 'Preview notify action is unavailable.',
  };
}

export function navigatePlugins(_event, _props, page) {
  const action = page?.getAction?.('preview.navigatePlugins');

  if (typeof action === 'function') {
    return action();
  }

  return {
    status: -1,
    msg: 'Preview navigation action is unavailable.',
  };
}
