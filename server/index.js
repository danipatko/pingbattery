import express from 'express';
import ewelink from 'ewelink-api';

const app = express();
// my socket's ID 1000cc4a9e

const connection =  new ewelink({ email: process.env.EWELINK_EMAIL || '', password: process.env.EWELINK_PASSWORD || '' });

// console.log(await connection.getDevice('1000cc4a9e'))

app.use(express.json());
app.use(express.urlencoded({ extended: false }));

/**
 * Switch socket on
 */
app.post('/:id/on', async (req, res, next)  => {
    // console.log(`Received request on for ${req.params.id}`); // DEBUG
    const device = await connection.getDevice(req.params.id);
    
    if(device && device.params && device.params.switch != 'on')
        await connection.toggleDevice(req.params.id)
    
    res.json();
});

/**
 * Switch socket off
 */
app.post('/:id/off', async (req, res, next)  => {
    // console.log(`Received request off for ${req.params.id}`); // DEBUG
    const device = await connection.getDevice(req.params.id);
    
    if(device && device.params && device.params.switch == 'on')
        await connection.toggleDevice(req.params.id)
    
    res.json();
});

//*/

app.listen(8000, '0.0.0.0', () => { 
    console.log('listening');
});

export default app;