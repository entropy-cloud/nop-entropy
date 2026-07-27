import { test } from '@nop-entropy/e2e-shared';
import { login } from '@nop-entropy/e2e-shared';

test('debug — check form submitAction in schema', async ({ page, engine }) => {
  let formSchema: unknown = null;
  await page.route('**/graphql', async (route) => {
    const body = route.request().postData() || '';
    if (body.includes('PageProvider__getPage')) {
      const response = await route.fetch();
      const json = await response.json();
      const data = json?.data?.['PageProvider__getPage'];
      // Find form schema
      if (data) {
        const findForms = (n: unknown, depth = 0): Record<string, unknown>[] => {
          if (!n || typeof n !== 'object' || depth > 20) return [];
          if (Array.isArray(n)) return n.flatMap(v => findForms(v, depth + 1));
          const o = n as Record<string, unknown>;
          const forms: Record<string, unknown>[] = [];
          if (o.type === 'form') forms.push(o);
          for (const v of Object.values(o)) forms.push(...findForms(v, depth + 1));
          return forms;
        };
        const forms = findForms(data);
        formSchema = forms[0] || null;
      }
      await route.fulfill({ response });
    } else {
      await route.continue();
    }
  });

  await login(page, { username: 'nop', password: '123' });
  await page.goto('#/NopAuthUser-main');
  await engine.crudContainer(page).waitFor({ state: 'visible' });
  await page.waitForTimeout(5000);

  if (!formSchema) { console.log('NO form schema captured'); return; }

  const fs = formSchema as Record<string, unknown>;
  console.log('has submitAction:', 'submitAction' in fs);
  console.log('submitAction type:', typeof fs.submitAction);
  console.log('has onSubmitSuccess:', 'onSubmitSuccess' in fs);
  console.log('onSubmitSuccess type:', typeof fs.onSubmitSuccess);
  console.log('form keys:', Object.keys(fs).filter(k => !['body','data'].includes(k)).join(', '));
});
