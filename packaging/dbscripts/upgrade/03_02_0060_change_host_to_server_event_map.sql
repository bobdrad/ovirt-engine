-- Change Host to Server in gluster related events
insert into event_map(event_up_name, event_down_name) values('GLUSTER_SERVER_ADD_FAILED', 'UNASSIGNED');
insert into event_map(event_up_name, event_down_name) values('GLUSTER_SERVER_REMOVE_FAILED', 'UNASSIGNED');

update event_subscriber set event_up_name = 'GLUSTER_SERVER_ADD_FAILED' where event_up_name = 'GLUSTER_HOST_ADD_FAILED';
update event_subscriber set event_up_name = 'GLUSTER_SERVER_REMOVE_FAILED' where event_up_name = 'GLUSTER_HOST_REMOVE_FAILED';

delete from event_map where event_up_name='GLUSTER_HOST_ADD_FAILED';
delete from event_map where event_up_name='GLUSTER_HOST_REMOVE_FAILED';
