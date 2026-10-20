import React from 'react';
import { act, mount, until } from '../testUtils';
import { MemoryRouter } from 'react-router-dom';
import RemotePage from './RemotePage';
import { api } from '../lib/api';
const mockSession = { key: 'test-account', scope: 'remote-test', controller: new AbortController() };
jest.mock('../context/AppContext', () => ({ useApp: () => ({ session: mockSession }) }));
jest.mock('../lib/api', () => ({ api: { get: jest.fn(), post: jest.fn() }, errMsg: error => error.message }));
let view;
beforeEach(() => { jest.clearAllMocks(); api.get.mockResolvedValue({ data: { sessions: [{ id: 'first', controllable: true, device: 'First' }, { id: 'chosen', controllable: true, device: 'Chosen' }] } }); api.post.mockResolvedValue({ data: { ok: true } }); });
afterEach(async () => { if (view) await view.close(); view = null; });
test('remote controls retain the client chosen on title details', async () => {
  view = await mount(<MemoryRouter initialEntries={['/remote?device=chosen']}><RemotePage /></MemoryRouter>);
  await until(() => !view.container.querySelector('[aria-label="pause"]').disabled);
  await act(async () => view.container.querySelector('[aria-label="pause"]').click());
  expect(api.post.mock.calls[0][1]).toEqual({ session_id: 'chosen', action: 'pause' });
});
test('a disconnected chosen device never silently redirects controls to another client', async () => {
  view = await mount(<MemoryRouter initialEntries={['/remote?device=missing']}><RemotePage /></MemoryRouter>);
  await until(() => view.container.textContent.includes('no longer connected'));
  expect(view.container.querySelector('[aria-label="pause"]').disabled).toBe(true);
  expect(api.post).not.toHaveBeenCalled();
});
